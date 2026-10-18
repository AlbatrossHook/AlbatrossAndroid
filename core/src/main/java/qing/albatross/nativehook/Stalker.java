/*
 * Copyright 2025 QingWan (qingwanmail@foxmail.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package qing.albatross.nativehook;

import java.util.List;

import qing.albatross.core.Albatross;

/**
 * 代码追踪(stalker)入口: 把 native stalker 引擎暴露给 Java,
 * 可收集 call/ret/exec/block/compile 事件流, 并通过 {@link StalkerTransformer}
 * 与 {@link StalkerCallout} 在 Java 里编写指令级追踪逻辑。
 *
 * <p>推荐使用 {@link #followFunction(long, StalkerConfig)}: 只在目标函数
 * 调用树内追踪, 进入前/返回后的 Java 线程 ART 代码完全不被追踪;
 * 目标函数退出时自动 unfollow, 递归/嵌套调用按深度计数处理。
 * 需要 Java 回调里按条件控制追踪窗口时, 可用
 * {@code AlbNative.hookInstruction} 的 onEnter 里 {@link #followCurrentThread}、
 * onLeave 里 {@link #unfollowCurrentThread} 组合出等价语义(见其 javadoc)。
 *
 * <p>约束:
 * <ul>
 *   <li>整进程同时只有一个 stalker 会话, 后配置者生效; 多线程并发
 *       followFunction 的目标函数时共享同一深度计数(v1 假设单线程调用)。</li>
 *   <li>事件在专用投递线程上批量回调({@link StalkerEventListener}), 追踪线程
 *       本身绝不回调 Java, 因此事件回调里可安全做任何事。</li>
 *   <li>{@link StalkerIterator#putCallout} 在 arm32 上暂不支持。</li>
 * </ul>
 */
public final class Stalker {

  private static volatile StalkerEventListener currentListener;
  private static Thread deliveryThread;

  static {
    deliveryThread = new Thread(Stalker::deliverLoop, "AlbatrossStalkerPoll");
    deliveryThread.setDaemon(true);
    deliveryThread.start();
  }

  private Stalker() {
  }

  /**
   * 按函数追踪(推荐入口): hook 目标函数地址, 进入时开始追踪、退出时自动结束。
   * 只有目标函数调用树内的代码被追踪, 之后的每次调用都会自动开关追踪。
   *
   * @param address 目标函数地址(可通过 DlInfo 查符号获得)
   * @return 句柄, unhook 撤销; 不要在目标函数执行中 unhook
   */
  public static StalkerFollowHandle followFunction(long address, StalkerConfig config) {
    if (address == 0)
      return null;
    applyConfig(config);
    long handle = AlbNative.stalkerFollowFunction(address, config.eventMask(),
        config.eventBatchSize, config.transformer);
    if (handle == 0)
      return null;
    return new StalkerFollowHandle(handle);
  }

  /** 按函数追踪的符号便捷方法: 在指定 so 中按符号查地址后 followFunction。 */
  public static StalkerFollowHandle followSymbol(String libName, String symbol, StalkerConfig config) {
    DlInfo dlInfo = AlbNative.openLib(libName);
    if (dlInfo == null)
      return null;
    long address = dlInfo.getSymbolAddress(symbol);
    dlInfo.close();
    if (address == 0)
      return null;
    return followFunction(address, config);
  }

  /**
   * 追踪当前线程的全部后续执行。会连带追踪 Java 线程上的 ART 代码, 建议先 excludeModule。
   *
   * <p>在 {@code AlbNative.hookInstruction} 的 onEnter/onLeave 回调内调用时自动延迟:
   * 实际 follow 推迟到回调分发返回之后(java_hook_enter/java_hook_leave 边界)执行,
   * 追踪从拦截器跳板直达目标函数——ART/JNI 分发路径(libart.so 与 JIT 代码缓存)
   * 在激活前已原生执行完毕, 不会出现在 trace 里, 因此<b>无需</b> excludeNamed
   * ("jit-code-cache")。推荐模式: onEnter 里 follow + onLeave 里
   * {@link #unfollowCurrentThread} = 精确追踪本次调用(等价 followFunction);
   * 不 unfollow 则跟随返回路径继续(返回 Java/JIT 仍需 exclude, 沿用裸 follow 约定)。
   * 嵌套回调里的请求在最外层边界生效。
   */
  public static void followCurrentThread(StalkerConfig config) {
    applyConfig(config);
    AlbNative.stalkerFollowMe(config.eventMask(), config.eventBatchSize, config.transformer);
  }

  /**
   * 结束当前线程的追踪。在 hookInstruction 回调内调用时同样延迟到回调边界生效;
   * 若目标函数返回路径上有 onLeave 回调, 追踪会在其分发前暂停, 不会翻译 Java 代码。
   */
  public static void unfollowCurrentThread() {
    AlbNative.stalkerUnfollowMe();
  }

  /**
   * 追踪其他线程(ptrace 感染方式, 仅限同进程线程)。
   * 注意: arm64 后端的跨线程 follow 尚未完工(会崩溃), 仅 x86/x64 可用。
   */
  public static void followThread(long threadId, StalkerConfig config) {
    applyConfig(config);
    AlbNative.stalkerFollowThread(threadId, config.eventMask(), config.eventBatchSize,
        config.transformer);
  }

  /** 结束指定线程的追踪。 */
  public static void unfollowThread(long threadId) {
    AlbNative.stalkerUnfollowThread(threadId);
  }

  /**
   * 排除内存区间: 区间内的代码原生执行、不产生事件。
   * 注意: 追踪链在排除区断裂——从排除区内跳出的调用(含 JNI 返回后再调用的
   * native 函数)不再被追踪, 除非追踪点(如 followFunction 的 hook)重新拉回。
   */
  public static void exclude(long start, long size) {
    AlbNative.stalkerExcludeRange(start, size);
  }

  /** 排除整个模块的可执行段, 如 "libart.so"(断链语义见 {@link #exclude})。 */
  public static void excludeModule(String moduleName) {
    AlbNative.stalkerExcludeModule(moduleName);
  }

  /**
   * 按子串排除可执行区间, 同时匹配文件路径与匿名区间名,
   * 如 "jit-code-cache"(匹配 [anon:dalvik-jit-code-cache])。
   * 追踪 Java 线程时应排除 JIT 代码缓存: 其内容会被 ART 持续失效与回收,
   * 重写执行必然崩溃(断链语义见 {@link #exclude})。
   */
  public static void excludeNamed(String nameSubstring) {
    AlbNative.stalkerExcludeNamed(nameSubstring);
  }

  /**
   * 圈定追踪内存区间: 与 {@link #exclude} 对偶的白名单。未配置任何 include
   * 范围时不生效(全追踪); 一旦配置, 集合外的调用目标原生执行真实函数、
   * 返回后继续追踪(与排除区同机制), 以此把翻译范围收敛到集合内。
   * 注意与 exclude 相同的边界: 过滤只发生在调用目标判定, 跳转与返回的
   * 续块不经判定仍会被翻译——从 so 外发起 follow 时入口块会追踪一小段,
   * 要严格从 so 内开始应配合 followSymbol/followFunction 指定集合内入口。
   * 多次调用为并集; exclude 永远优先, 可用 {@link #exclude} 在 include
   * 集合内挖洞。范围在调用时快照, 应在 follow 之前配置; arm32 不支持。
   */
  public static void include(long start, long size) {
    AlbNative.stalkerIncludeRange(start, size);
  }

  /**
   * 圈定整个模块的可执行段, 如 "libfoo.so"(路径子串匹配, "foo" 亦可)。
   * 只有执行位于该模块内才被追踪; 组合语义见 {@link #include}。
   */
  public static void includeModule(String moduleName) {
    AlbNative.stalkerIncludeModule(moduleName);
  }

  /** 清空 include 白名单, 恢复全追踪(不影响 exclude 配置)。 */
  public static void clearInclusions() {
    AlbNative.stalkerClearInclusions();
  }

  /** 清空 exclude 排除列表(不影响 include 配置)。 */
  public static void clearExclusions() {
    AlbNative.stalkerClearExclusions();
  }

  /** 代码已变更时使对应块的缓存失效, 重新编译。 */
  public static void invalidate(long address) {
    AlbNative.stalkerInvalidate(address);
  }

  /** 回收不再被引用的生成代码块。 */
  public static boolean garbageCollect() {
    return AlbNative.stalkerGarbageCollect();
  }

  /**
   * 信任阈值: 块被复用多少次后保留原始代码快照以检测自修改代码。
   * 0 表示不检测, 越大越快但自修改代码可能出错, 默认 1。
   */
  public static void setTrustThreshold(int threshold) {
    AlbNative.stalkerSetTrustThreshold(threshold);
  }

  /** 暂停当前线程的追踪(与 deactivate 前的 activate 点衔接)。 */
  public static void deactivate() {
    AlbNative.stalkerDeactivate();
  }

  /** 从指定地址恢复当前线程的追踪。 */
  public static void activate(long target) {
    AlbNative.stalkerActivate(target);
  }

  /** 把 native 侧尚未投递的事件立即封批(通常在 unfollow 后调用以取回尾部事件)。 */
  public static void flushEvents() {
    AlbNative.stalkerFlushEvents();
  }

  /**
   * 追踪窗口时长(毫秒): 到点自动停止所有追踪、解除 followFunction 武装, 并向事件流
   * 尾部投递一条 {@link StalkerEvent.Type#STOPPED}(原因 {@link StalkerEvent#REASON_TIMEOUT})。
   * 默认 60000 兜底; 显式 0 = 不限时。
   *
   * <p>本调用同时开启新一轮窗口: 清除上一次的停止状态, 之前被超时解除武装的
   * followFunction 钩子重新可用(不必重新 followFunction)。
   */
  public static void setTimeout(int timeoutMs) {
    AlbNative.stalkerSetTimeout(timeoutMs);
  }

  /**
   * 事件数上限(0 = 不限): 抓够该数量事件后自动停止, 停止原因
   * {@link StalkerEvent#REASON_MAX_EVENTS}。软上限(5ms 轮询), 可能略超。
   *
   * <p>注意: 这是"下一次 follow 之前"的临时设置——follow 时
   * {@link StalkerConfig#maxEvents} 会覆盖它(该字段默认 0 = 不限);
   * 要长期生效请写进 config。
   */
  public static void setMaxEvents(int maxEvents) {
    AlbNative.stalkerSetMaxEvents(maxEvents);
  }

  /** 上次窗口的停止原因: 0 无(用户 unfollow/目标退出) / 1 超时 / 2 事件数上限。 */
  public static int getStopReason() {
    return AlbNative.stalkerGetStopReason();
  }

  /** 本次窗口内已投递的事件数(窗口开始/重新开窗时清零)。 */
  public static long getEventCount() {
    return AlbNative.stalkerGetEventCount();
  }

  private static void applyConfig(StalkerConfig config) {
    // 追踪窗口: 超时兜底 + 事件数上限(见 StalkerConfig.timeoutMs / maxEvents)
    AlbNative.stalkerSetTimeout(config.timeoutMs);
    AlbNative.stalkerSetMaxEvents(config.maxEvents);
    StalkerEventListener listener = config.eventListener;
    currentListener = listener;
    if (listener == null) {
      // 无监听时直接停掉投递循环的消费端, 避免事件积压
      AlbNative.stalkerStopSession();
    }
  }

  private static void deliverLoop() {
    while (true) {
      long[] batch;
      try {
        batch = AlbNative.stalkerPollEvents();
      } catch (UnsatisfiedLinkError | IllegalStateException e) {
        // native 未注册或进程关闭阶段
        sleepQuietly(50);
        continue;
      }
      if (batch == null || batch.length == 0) {
        sleepQuietly(2);
        continue;
      }
      StalkerEventListener listener = currentListener;
      if (listener != null) {
        try {
          List<StalkerEvent> events = StalkerEvent.decode(batch);
          if (!events.isEmpty())
            listener.onEvents(events);
        } catch (Throwable t) {
          Albatross.log("stalker deliver events fail", t);
        }
      }
    }
  }

  private static void sleepQuietly(long millis) {
    try {
      Thread.sleep(millis);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }
}
