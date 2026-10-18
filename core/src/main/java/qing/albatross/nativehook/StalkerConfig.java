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

/**
 * stalker 追踪配置: 事件开关、可选的指令级 transformer 与事件回调。
 * 整个进程同时只有一个 stalker 会话, 后配置者生效。
 */
public final class StalkerConfig {

  /** 追踪 CALL 事件(调用指令) */
  public boolean callEvents;
  /** 追踪 RET 事件(返回指令) */
  public boolean retEvents;
  /** 追踪 EXEC 事件(每条指令, 数量巨大, 谨慎开启) */
  public boolean execEvents;
  /** 追踪 BLOCK 事件(基本块) */
  public boolean blockEvents;
  /** 追踪 COMPILE 事件(块编译完成) */
  public boolean compileEvents;
  /** 批量事件缓冲容量(事件数), 缓冲满后投递线程可取到一批 */
  public int eventBatchSize = 4096;
  // 坑见 doc/pitfalls/pitfall-trace-window-timeout-20261004.md（默认 60s 兜底 / 只 disarm 不 unhook）
  /**
   * 追踪窗口时长(毫秒): 到点自动停止(停止原因 timeout)。
   * 默认 60000 兜底, 防止"忘了停"的追踪一直跑; 显式传 0 = 不限时。
   * 窗口从第一次真正 follow 起算(followFunction 即目标函数第一次被调用时);
   * 目标函数始终没被调用就一直没有窗口计时, 超时自然也不会触发。
   * 触发后只断事件流不卸载 hook, 重新开窗调 {@link Stalker#setTimeout(int)},
   * 或直接再 followFunction 一次(显式 follow = 新会话); hook 内按次
   * follow/unfollow 的写法每次 follow 都算新窗口(兜底拦不住累计时长)。
   */
  public int timeoutMs = 60000;
  /**
   * 事件数上限: 抓到该数量事件后自动停止(停止原因 max_events), 0 = 不限。
   * 软上限(watchdog 5ms 轮询), 实际可能略超。防热循环把内存/日志打爆。
   */
  public int maxEvents = 0;
  /** 指令级 transformer, null 表示保留全部指令不做定制 */
  public StalkerTransformer transformer;
  /** 事件回调(在专用投递线程上调用, 不在追踪线程上) */
  public StalkerEventListener eventListener;

  /** 见 {@link #timeoutMs}; 0 = 不限时 */
  public StalkerConfig timeout(int timeoutMs) {
    this.timeoutMs = timeoutMs;
    return this;
  }

  /** 见 {@link #maxEvents}; 0 = 不限 */
  public StalkerConfig maxEvents(int maxEvents) {
    this.maxEvents = maxEvents;
    return this;
  }

  public StalkerConfig call(boolean enable) {
    this.callEvents = enable;
    return this;
  }

  public StalkerConfig ret(boolean enable) {
    this.retEvents = enable;
    return this;
  }

  public StalkerConfig exec(boolean enable) {
    this.execEvents = enable;
    return this;
  }

  public StalkerConfig block(boolean enable) {
    this.blockEvents = enable;
    return this;
  }

  public StalkerConfig compile(boolean enable) {
    this.compileEvents = enable;
    return this;
  }

  int eventMask() {
    int mask = 0;
    if (callEvents)
      mask |= StalkerEvent.Type.CALL.mask;
    if (retEvents)
      mask |= StalkerEvent.Type.RET.mask;
    if (execEvents)
      mask |= StalkerEvent.Type.EXEC.mask;
    if (blockEvents)
      mask |= StalkerEvent.Type.BLOCK.mask;
    if (compileEvents)
      mask |= StalkerEvent.Type.COMPILE.mask;
    return mask;
  }
}
