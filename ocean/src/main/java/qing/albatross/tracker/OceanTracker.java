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
package qing.albatross.tracker;

import android.app.Application;
import android.os.Handler;
import android.os.Looper;

import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.util.Enumeration;

import dalvik.system.BaseDexClassLoader;
import dalvik.system.DexFile;
import qing.albatross.annotation.ByName;
import qing.albatross.annotation.ExecutionOption;
import qing.albatross.annotation.ExecutionMode;
import qing.albatross.classloader.BaseDexClassLoaderH;
import qing.albatross.classloader.Element;
import qing.albatross.common.SafeToString;
import qing.albatross.common.ThreadConfig;
import qing.albatross.core.Albatross;
import qing.albatross.core.InvocationContext;
import qing.albatross.core.MethodInvokeFrame;
import qing.albatross.exception.AlbatrossErr;
import qing.albatross.reflection.BooleanMethodDef;
import qing.albatross.reflection.VoidMethodDef;

public class OceanTracker {

  public static final int DUMP_SMAIL = 1;
  public static final int DUMP_DEX = 2;

  public static boolean dumpSamil = false;


  private static class H {
    @ByName
    public static VoidMethodDef onCall;
    @ByName
    public static VoidMethodDef onExec;
    @ByName
    public static VoidMethodDef onBlock;
    @ByName
    public static VoidMethodDef onFieldWritten;
    @ByName
    public static VoidMethodDef onFieldWrittenPrim;
    @ByName
    public static VoidMethodDef onFieldRead;
    @ByName
    public static VoidMethodDef onReturn;
    @ByName
    public static VoidMethodDef onReturnPrim;
    @ByName
    public static VoidMethodDef onUnTrace;
    @ByName
    public static BooleanMethodDef onTrace;
  }

  static boolean isInit;

  public static boolean init(int flags) {
    if (isInit)
      return true;
    Albatross.insHookInit();
    Albatross.registerOceanTracker(OceanTracker.class);
    try {
      Albatross.hookClass(H.class, SinkerDelegate.class);
      Albatross.hookClass(BaseDexClassLoaderH.class, BaseDexClassLoader.class);
    } catch (AlbatrossErr e) {
      return false;
    }
    try {
      registerMethod(H.onFieldRead.method, H.onFieldWritten.method,
          H.onFieldWrittenPrim.method, H.onBlock.method, H.onCall.method, H.onExec.method,
          H.onReturn.method, H.onReturnPrim.method, H.onTrace.method, H.onUnTrace.method);
    } catch (Exception e) {
      return false;
    }
    addWhiteClass(InvocationContext.class);
    addWhiteClass(Class.class);
    addWhiteClass(String.class);
    addWhiteClass(MethodInvokeFrame.class);
    addWhiteClass(Albatross.class);
    addWhiteClass(SinkerDelegate.class);
    addWhiteClass(OceanSinker.class);
    Package thredConfigPackage = ThreadConfig.class.getPackage();
    if (thredConfigPackage != null && !thredConfigPackage.getName().startsWith("qing.albatross"))
      addWhitePackage(thredConfigPackage.getName());
    else
      addWhitePackage("qing.albatross.");
    try {
      addWhiteClass(Class.forName("java.lang.AbstractStringBuilder"));
    } catch (ClassNotFoundException e) {
    }
    Albatross.compileClassByAnnotation(OceanTracker.class, ExecutionOption.DO_NOTHING);
    SafeToString.compile();
    isInit = true;
    return true;
  }

  private static native boolean traceMeNative(Object sinker, int event);

  private static native boolean stopTraceMeNative();

  private static native long traceMemberNative(int pid, Member member, OceanSinker sinker, int event, int option);

  private static native void registerMethod(Method fieldRead, Method fieldWritten, Method fieldWrittenPrimitive,
                                            Method block, Method call, Method exec, Method ret, Method retRaw,
                                            Method onTrace, Method onUnTrace);

  private static native boolean traceGlobalNative(Object sinker, int event);

  private static native boolean stopTraceGlobalNative();

  // ---- 追踪窗口(定时/事件数上限): 见 traceMe / traceGlobal / setTraceTimeout ----

  /** 未显式设置超时的会话兜底时长(毫秒), 防止"忘了停"的追踪一直跑 */
  public static final int DEFAULT_TIMEOUT_MS = 60000;
  /** 停止原因(见 traceStatus) */
  public static final int REASON_NONE = 0;
  public static final int REASON_TIMEOUT = 1;
  public static final int REASON_MAX_EVENTS = 2;

  /** 窗口时长(毫秒): -1 = 未显式设置(用 {@link #DEFAULT_TIMEOUT_MS} 兜底), 0 = 不限时, >0 = 毫秒 */
  private static volatile int traceTimeoutMs = -1;
  /** 当前窗口生效的时长(窗口启动时落位; 状态展示用) */
  private static volatile int windowTimeoutMs = DEFAULT_TIMEOUT_MS;
  /** 事件数上限, 0 = 不限 */
  private static volatile int traceMaxEvents = 0;
  /** 窗口起点(单调时钟毫秒) */
  private static volatile long traceStartMs = 0;
  /** 窗口已到期/达上限: 停止已请求(事件回调据此丢弃后续事件) */
  private static volatile boolean stopRequested = false;
  private static volatile int stopReason = REASON_NONE;
  /** 全局追踪的 sinker: 事件回调据此判断停止该走 global 还是当前线程 */
  private static volatile OceanSinker globalSinker;
  /** 本窗口已投递的事件数(全局追踪多线程共享) */
  private static final java.util.concurrent.atomic.AtomicInteger windowEventCount =
      new java.util.concurrent.atomic.AtomicInteger();
  /** 追踪是否在进行(线程级或全局; 仅作状态展示) */
  private static volatile boolean traceRunning = false;

  /**
   * 设置追踪窗口时长(毫秒): 0 = 不限时。可在追踪开始前设置(对之后启动的追踪生效),
   * 也可在追踪进行中调用(从现在起按新值重新计时开一个窗口)。
   */
  public static void setTraceTimeout(int timeoutMs) {
    traceTimeoutMs = timeoutMs;
    windowTimeoutMs = resolveTimeout(timeoutMs);
    traceStartMs = android.os.SystemClock.uptimeMillis();
    stopRequested = false;
    stopReason = REASON_NONE;
  }

  private static int resolveTimeout(int timeoutMs) {
    return timeoutMs < 0 ? DEFAULT_TIMEOUT_MS : timeoutMs;
  }

  /** 设置事件数上限(0 = 不限): 抓到该数量事件后自动停止 */
  public static void setTraceMaxEvents(int maxEvents) {
    traceMaxEvents = maxEvents > 0 ? maxEvents : 0;
  }

  /** 当前窗口生效的时长(毫秒, 0 = 不限) */
  public static int getTraceTimeout() {
    return windowTimeoutMs;
  }

  public static int getTraceMaxEvents() {
    return traceMaxEvents;
  }

  /** 上次窗口的停止原因: REASON_NONE(未自动停止) / REASON_TIMEOUT / REASON_MAX_EVENTS */
  public static int getStopReason() {
    return stopReason;
  }

  public static boolean isTraceRunning() {
    return traceRunning;
  }

  /** 窗口起点重设: 追踪启动时调用(traceMe/traceGlobal 内部) */
  static void startTraceWindow(int timeoutMs, boolean global) {
    traceTimeoutMs = timeoutMs;
    windowTimeoutMs = resolveTimeout(timeoutMs);
    traceStartMs = android.os.SystemClock.uptimeMillis();
    stopRequested = false;
    stopReason = REASON_NONE;
    windowEventCount.set(0);
    if (global)
      globalSinker = null; /* 由 traceGlobal 在启动成功后写入 */
  }

  /** 本窗口事件计数(事件回调入口调用; 全局追踪时多线程共享, 原子累加) */
  static int countWindowEvent() {
    return windowEventCount.incrementAndGet();
  }

  /**
   * 窗口停止原因(只看时间/计数, 不改变状态): 0 = 未到期。
   * events 传 -1 表示只看时间。
   */
  public static int windowStopReason(int events) {
    if (stopRequested)
      return stopReason;
    int timeoutMs = windowTimeoutMs;
    if (timeoutMs > 0) {
      long elapsed = android.os.SystemClock.uptimeMillis() - traceStartMs;
      if (elapsed >= timeoutMs)
        return REASON_TIMEOUT;
    }
    int maxEvents = traceMaxEvents;
    if (events >= 0 && maxEvents > 0 && events >= maxEvents)
      return REASON_MAX_EVENTS;
    return REASON_NONE;
  }

  /** 本窗口开始到现在的毫秒数(未启动过追踪时返回 0) */
  public static long getWindowElapsedMs() {
    long start = traceStartMs;
    return start == 0 ? 0 : android.os.SystemClock.uptimeMillis() - start;
  }

  /** 本窗口已投递的事件数 */
  public static int getWindowEventCount() {
    return windowEventCount.get();
  }

  /**
   * 窗口已过/达上限时记录停止原因(状态查询补停用): 记录后事件回调会丢弃后续事件,
   * 实际拆除追踪由调用方按模式执行(见 AppInjectAgent.traceStatus)。
   */
  public static void noteStopReason(int reason) {
    stopRequested = true;
    stopReason = reason;
  }

  /** 窗口是否已到期/达上限(不改变状态) */
  static boolean windowExpired() {
    return windowStopReason(-1) != REASON_NONE;
  }

  /** 窗口是否已到期/达上限(在事件回调里调用, 运行在被追踪线程上) */
  static int checkWindow(OceanSinker sinker, int events) {
    if (stopRequested)
      return stopReason != REASON_NONE ? stopReason : REASON_TIMEOUT;
    return windowStopReason(events);
  }

  /**
   * 窗口到期/达上限: 记原因并请求停止(在事件回调里调用)。
   * 线程级追踪直接停当前线程(当前线程即被追踪线程); 全局追踪交给主线程停,
   * 避免在监听器分发过程中拆监听器。
   */
  // 坑见 doc/pitfalls/pitfall-trace-window-timeout-20261004.md（主线程停止要投递）
  static void requestStop(OceanSinker sinker, int reason) {
    if (stopRequested)
      return;
    stopRequested = true;
    stopReason = reason;
    /*
     * 全局追踪与"主线程被追踪"都交给主线程稍后执行: 栈上还有插桩帧时在监听器
     * 分发中途拆监听器不安全; 其他线程没有可投递的 Looper, 只能直接停当前线程
     * (当前线程即被追踪线程)。
     * 记下 stopRequested 后事件回调立即开始丢弃事件, 拆除晚几毫秒不影响事件流
     * 在窗口到期时就断。
     *
     * 没有 ActivityThread 的进程（load_jvm 之后的原生宿主/无 Activity 环境）里
     * getMainHandler() 会 NPE 且投递的任务也没人跑：用 mainHandlerOrNull()，取不到
     * 就按"其他线程"的既有降级路径直接停（停止本身只 disarm + 延迟拆除，可从被追踪
     * 线程调用）。
     * 坑见 doc/pitfalls/pitfall-java-host-route-no-activitythread-20261010.md
     */
    Handler mainHandler = Albatross.mainHandlerOrNull();
    if (mainHandler == null) {
      if (sinker != null && sinker == globalSinker)
        stopTraceGlobal();
      else
        stopTraceMe();
    } else if (sinker != null && sinker == globalSinker) {
      mainHandler.post(OceanTracker::stopTraceGlobal);
    } else if (Looper.myLooper() == Looper.getMainLooper()) {
      mainHandler.post(OceanTracker::stopTraceMe);
    } else {
      stopTraceMe();
    }
  }

  /** 停止原因的中文名(日志/状态用) */
  public static String stopReasonName(int reason) {
    switch (reason) {
      case REASON_TIMEOUT:
        return "timeout";
      case REASON_MAX_EVENTS:
        return "max_events";
      default:
        return "none";
    }
  }

  public static native boolean dumpMethod(Member method);

  public static native boolean dumpClass(Class<?> method);

  public static native void setDumpConfig(String output, int dumpOption);

  public static void setDumpConfig(String output) {
    dumpSamil = true;
    setDumpConfig(output, DUMP_SMAIL);
  }

  private static native void finishDumpNative();

  public static native boolean isCurrentThreadTraced();

  public static native void decompileAll(int scope);

  public static native boolean addWhiteClass(Class<?> clz);

  public static native boolean addWhitePackage(String packagePrefix);

  public static native boolean removeWhitePackage(String packagePrefix);

  public static native int getTraceCount();

  public static native void syncTrace();

  public static native boolean removeWhiteClass(Class<?> clz);

  private static native void setTraceScopeNative(int scope);

  static native void processMethod(long contextId, boolean bypass);

  public static void finishDump() {
    Application application = Albatross.currentApplication();
    dumpClassLoader((BaseDexClassLoader) application.getClassLoader());
    finishDumpNative();
  }

  public static final int kJdk = 1;
  public static final int kPlatform = 2;
  public static final int kApplication = 4;
  public static final int kPackageFilter = 8;
  public static final int kEntryOnly = 16;
//  public static final int kAll = kJdk | kPlatform | kApplication;


  public static void setTraceScope(int scope) {
    setTraceScopeNative(scope);
  }


  private static SinkerDelegate newSinker(OceanSinker sinker, int event) {
    SinkerDelegate threadSinker = new SinkerDelegate();
    if ((event & (EventType.EXEC | EventType.BLOCK)) != 0) {
      threadSinker.traceContext = new ThreadLocal<>();
    }
    if ((event & (EventType.RETURN)) != 0) {
      threadSinker.returnContext = new ThreadLocal<>();
    }
    if ((event & (EventType.CALL)) != 0) {
      threadSinker.callContext = new ThreadLocal<>();
    }
    if ((event & (EventType.FILE_READ)) != 0) {
      threadSinker.fieldReadEvent = new ThreadLocal<>();
    }
    if ((event & (EventType.FIELD_WRITE)) != 0) {
      threadSinker.fieldWriteEvent = new ThreadLocal<>();
    }
    threadSinker.sinker = sinker;
    return threadSinker;
  }

  /** 追踪当前线程(使用当前窗口配置: 默认 60s 兜底) */
  public static boolean traceMe(OceanSinker sinker, int event) {
    return traceMe(sinker, event, traceTimeoutMs);
  }

  /**
   * 追踪当前线程, 指定窗口时长: timeoutMs 毫秒后自动停止(0 = 不限时)。
   * 到点由事件回调触发停止(见 {@link #setTraceTimeout}), 停止原因
   * {@link #REASON_TIMEOUT}; 之后可用 {@link #getStopReason()} 查询。
   */
  public static boolean traceMe(OceanSinker sinker, int event, int timeoutMs) {
    if (isCurrentThreadTraced())
      return false;
    SinkerDelegate threadSinker = newSinker(sinker, event);
    startTraceWindow(timeoutMs, false);
    boolean res = traceMeNative(threadSinker, event);
    traceRunning = res;
    return res;
  }

  /** 追踪全局(所有线程), 使用当前窗口配置(默认 60s 兜底) */
  public static boolean traceGlobal(OceanSinker sinker, int event) {
    return traceGlobal(sinker, event, traceTimeoutMs);
  }

  /** 追踪全局(所有线程), 指定窗口时长; 语义见 {@link #traceMe(OceanSinker, int, int)} */
  public static boolean traceGlobal(OceanSinker sinker, int event, int timeoutMs) {
    if (isCurrentThreadTraced())
      return false;
    SinkerDelegate threadSinker = newSinker(sinker, event);
    startTraceWindow(timeoutMs, true);
    boolean res = traceGlobalNative(threadSinker, event);
    if (res) {
      globalSinker = sinker;
      traceRunning = true;
    }
    return res;
  }

  /** 停止当前线程的追踪 */
  public static boolean stopTraceMe() {
    traceRunning = false;
    return stopTraceMeNative();
  }

  /** 停止全局追踪 */
  public static boolean stopTraceGlobal() {
    traceRunning = false;
    globalSinker = null;
    return stopTraceGlobalNative();
  }


  @ExecutionMode(ExecutionOption.DISABLE_AOT)
  public static void dumpClassLoader(BaseDexClassLoader dexLoader) {
    BaseDexClassLoaderH loader = Albatross.convert(dexLoader, BaseDexClassLoaderH.class);
    Element[] dexElements = loader.pathList.dexElements;
    for (Element dexElement : dexElements) {
      DexFile d = dexElement.dexFile;
      if (d == null)
        continue;
      Enumeration<String> dexEntries = d.entries();
      while (dexEntries.hasMoreElements()) {
        String className = dexEntries.nextElement();
        try {
          if (className.startsWith("android."))
            continue;
          Class<?> clz = Class.forName(className, false, dexLoader);
          if (dumpSamil)
            OceanTracker.dumpClass(clz);
          else {
            Method[] methods = clz.getDeclaredMethods();
            if (methods.length > 0) {
              OceanTracker.dumpMethod(methods[0]);
//            break;
            }
          }
        } catch (Throwable ignore) {
        }
      }
    }
  }


}
