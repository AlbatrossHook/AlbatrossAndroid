package qing.albatross.demo;

import qing.albatross.core.Albatross;
import qing.albatross.nativehook.Stalker;
import qing.albatross.nativehook.StalkerConfig;
import qing.albatross.nativehook.StalkerEvent;
import qing.albatross.nativehook.StalkerFollowHandle;

/**
 * stalker Java API 验证:
 * 用例 1 followFunction: 按函数追踪(推荐用法), 含 transformer/callout 改寄存器;
 * 用例 2 followThread: 跨线程追踪纯 native 线程;
 * 用例 3 followCurrentThread: 裸 follow 当前 Java 线程(排除 JIT 代码缓存; 断言会话
 *                              可用与事件投递, 分发可见性边界见用例内说明);
 * 用例 4 事件完整性: exec/block/compile/depth 五种事件与嵌套深度;
 * 用例 5 callout 读寄存器: pc 一致性/参数读取/无效名异常/SP/FP;
 * 用例 6 递归与多轮: 递归函数深度计数正确性 + 多轮调用的 callout 计数;
 * 用例 7 followSymbol 与辅助 API: 符号查址追踪 + trust/gc/invalidate/幂等 unhook;
 * 用例 8 includeModule 白名单: 只追踪指定 so + exclude 在集合内挖洞。
 */
public class StalkerTest {

  static native int targetFunction(int n);

  static native long targetAddress();

  static native long innerAddress();

  static native int fib(int n);

  static native long fibAddress();

  static native long startTargetThread();

  static native void releaseTargetThread();

  static native int joinTargetThread();

  static volatile int eventCount;
  static volatile boolean sawInnerCall;
  // 用例 8: inner 体内是否出现 EXEC 事件 —— 在回调里按地址区间直接判定。
  // 不能在回调里攒 location 列表、在主线程遍历: 事件由投递线程并发写入,
  // 遍历必撞 ConcurrentModificationException; 而且 include 阶段的事件量很大
  // (实测 3.6M 个 Long 装箱对象/153MB), 会把堆撑到一次 61s 的后台 GC。
  static volatile boolean sawExecInInner;
  static volatile int execEventCount;
  static volatile int transformedBlocks;
  static volatile boolean calloutFired;
  // 用例 4: 事件类型完整性
  static volatile boolean sawExec, sawRetEvent, sawBlockEvent, sawCompileEvent;
  static volatile int maxCallDepth;
  // 用例 5: callout 读寄存器
  static volatile boolean calloutPcCorrect;
  static volatile boolean calloutSpSeen, calloutFpSeen;
  static volatile boolean invalidRegisterNameThrew;
  static volatile boolean lrBehaved;
  static final StringBuilder calloutArgsLog = new StringBuilder();
  // 用例 6: 递归与多轮
  static volatile int calloutCount;

  // target(n) = (inner(n) + inner(n+1)) - inner(n-1), inner(n) = n*10+1
  static int expected(int n) {
    return (n * 10 + 1) + ((n + 1) * 10 + 1) - ((n - 1) * 10 + 1);
  }

  static String firstArgRegister() {
    String arch = System.getProperty("os.arch", "");
    if (arch.contains("aarch64") || arch.contains("arm64"))
      return "x0";
    if (arch.contains("arm"))
      return "r0";
    return "rdi";
  }

  static void check(boolean cond, String message) {
    if (!cond)
      throw new AssertionError(message);
  }

  static void resetStats() {
    eventCount = 0;
    sawInnerCall = false;
    sawExecInInner = false;
    execEventCount = 0;
    transformedBlocks = 0;
    calloutFired = false;
    sawExec = sawRetEvent = sawBlockEvent = sawCompileEvent = false;
    maxCallDepth = 0;
    calloutPcCorrect = true;
    calloutSpSeen = false;
    calloutFpSeen = false;
    invalidRegisterNameThrew = false;
    lrBehaved = true;
    calloutArgsLog.setLength(0);
    calloutCount = 0;
  }

  static void waitEvents(int timeoutMs) throws InterruptedException {
    long deadline = System.currentTimeMillis() + timeoutMs;
    while (System.currentTimeMillis() < deadline) {
      if (eventCount > 0)
        return;
      Thread.sleep(10);
    }
  }

  public static void test() throws Exception {
    TestRunner.run("followFunction", StalkerTest::testFollowFunction);
    TestRunner.run("followThread", StalkerTest::testFollowThread);
    TestRunner.run("followCurrentThread", StalkerTest::testFollowCurrentThread);
    TestRunner.run("eventCompleteness", StalkerTest::testEventCompleteness);
    TestRunner.run("calloutReadRegisters", StalkerTest::testCalloutReadRegisters);
    TestRunner.run("recursionAndRounds", StalkerTest::testRecursionAndRounds);
    TestRunner.run("followSymbolAndUtilities", StalkerTest::testFollowSymbolAndUtilities);
    TestRunner.run("includeModule", StalkerTest::testIncludeModule);
  }

  static void testFollowFunction() throws Exception {
    resetStats();
    long address = targetAddress();
    long inner = innerAddress();
    check(address != 0, "targetAddress == 0");

    StalkerConfig config = new StalkerConfig().call(true).ret(true).block(true);
    config.transformer = iterator -> {
      transformedBlocks++;
      while (iterator.next()) {
        // callout 放在未被 interceptor patch 的 inner 函数首指令:
        // 目标函数首指令被 hook 替换, stalker 从 hook 点之后接管, 永远编译不到它
        if (iterator.address() == inner) {
          iterator.putCallout(cpu -> {
            calloutFired = true;
            /* 按序号写参（ABI 无关）：i386 上参数在栈上，按名写 "rdi" 不存在；
             * arm64/x64 按序号同样是各自的参数寄存器。 */
            cpu.setArgument(0, 42);
          });
        }
        iterator.keep();
      }
    };
    config.eventListener = events -> {
      eventCount += events.size();
      for (StalkerEvent event : events) {
        if (event.type == StalkerEvent.Type.CALL && event.target == inner)
          sawInnerCall = true;
      }
    };

    StalkerFollowHandle handle = Stalker.followFunction(address, config);
    check(handle != null, "followFunction returned null");
    try {
      int result = targetFunction(5);
      // target(5) = (inner(5) + inner(6)) - inner(4), 三次 inner 调用
      // 都被 callout 改参 42: (421 + 421) - 421 = 421
      check(result == 421, "callout did not rewrite arg: " + result);
      check(calloutFired, "callout never fired");
      waitEvents(5000);
      Stalker.flushEvents();
      Thread.sleep(300);
      check(sawInnerCall, "no inner call event observed");
      check(transformedBlocks > 0, "transformer never invoked: " + transformedBlocks);
      check(eventCount > 0, "no events delivered");
    } finally {
      handle.unhook();
    }

    // unhook 后再调用: 行为恢复且不再产生事件
    int after = targetFunction(5);
    check(after == expected(5), "unhook did not restore behavior: " + after);
    Thread.sleep(300);
  }

  static void testFollowThread() throws Exception {
    String arch = System.getProperty("os.arch", "");
    if (arch.contains("aarch64") || arch.contains("arm64")) {
      // stalker arm64 后端的跨线程 follow(infect) 未完工:
      // C 测试里 follow_thread 也被注释禁用(stalker_arm64_test.cpp)
      throw TestRunner.skip("arm64 后端 follow(tid) 未完工");
    }
    if (arch.contains("i386") || arch.contains("i486") || arch.contains("i586")
        || arch.contains("i686") || arch.equals("x86")) {
      /* i386 后端跨线程 follow 无事件投递: native C 用例同断言在纯 HEAD 上
       * 实测 events.size()==0（/tmp/alb-verify-jvm 隔离副本复现），属后端
       * 既有缺口（对照 x86_64 通过）；缺口记录见
       * doc/pitfalls/pitfall-x86-32-trace-aot-gaps-20261013.md */
      throw TestRunner.skip("i386 后端 follow(tid) 无事件投递（既有缺口，见 pitfall）");
    }
    resetStats();
    long inner = innerAddress();
    long tid = startTargetThread();
    check(tid > 0, "startTargetThread failed");

    StalkerConfig config = new StalkerConfig().call(true).ret(true).block(true);
    config.eventListener = events -> {
      eventCount += events.size();
      for (StalkerEvent event : events) {
        if (event.type == StalkerEvent.Type.CALL && event.target == inner)
          sawInnerCall = true;
      }
    };
    Stalker.followThread(tid, config);
    try {
      releaseTargetThread();
      int result = joinTargetThread();
      check(result == expected(5), "unexpected native thread result: " + result);
    } finally {
      Stalker.unfollowThread(tid);
    }
    waitEvents(5000);
    Stalker.flushEvents();
    Thread.sleep(300);
    check(sawInnerCall, "no inner call event from followed thread");
    check(eventCount > 0, "no events from followed thread");
  }

  static void testFollowCurrentThread() throws Exception {
    resetStats();
    long inner = innerAddress();
    // 裸 follow 会连带追踪 Java 线程上的 ART 代码。
    // 不能 exclude libart: follow 的 JNI 返回路径进入排除区会断链(排除区
    // 跳出的调用不再被追踪); 必须排除 JIT 代码缓存: 其内容被 ART 持续
    // 失效与回收, stalker 重写执行必然崩溃
    Stalker.excludeNamed("jit-code-cache");
    StalkerConfig config = new StalkerConfig().call(true);
    config.eventListener = events -> {
      eventCount += events.size();
      for (StalkerEvent event : events) {
        if (event.type == StalkerEvent.Type.CALL && event.target == inner)
          sawInnerCall = true;
      }
    };
    Stalker.followCurrentThread(config);
    int result;
    try {
      result = targetFunction(5);
    } finally {
      Stalker.unfollowCurrentThread();
    }
    check(result == expected(5), "unexpected result during bare follow: " + result);
    waitEvents(10000);
    Stalker.flushEvents();
    Thread.sleep(500);
    /*
     * 可见性边界(2026-10-10 复核): Albatross.init 之后, 解释器的 DoCall 分发被框架
     * 的 Java hook 机制接管(intercept_init → hookDoCall, 分发代码在框架库内), 而
     * 框架库对 stalker 自排除(自身代码不被翻译) —— 凡是经该分发执行的被调方
     * (Java 方法体与 JNI native 方法)都整段原生执行, 裸 follow 只能看到解释器
     * 分发前后的转移, 看不到 targetFunction 内部的 inner 调用。实测(API29 x86_64
     * 模拟器, 单类隔离跑)26 个 CALL 事件全部指向解释器/框架分发, 无一个落在 demo
     * so, 且确定性复现。因此本用例断言"裸 follow 会话本身可用"(有事件、结果正确、
     * unfollow 干净); inner 调用事件由追踪起点就在 native 代码内的 followFunction
     * (用例 1/4/8)与 followThread(用例 2)覆盖。
     * 能力缺口记录: doc/framework_feedback/req-bare-follow-interpreter-dispatch-20261010.md
     */
    Albatross.log("followCurrentThread: events=" + eventCount + " innerCall=" + sawInnerCall);
    check(eventCount > 0, "no events during bare follow");
  }

  /** 用例 4: followFunction 窗口内五种事件类型与调用深度。 */
  static void testEventCompleteness() throws Exception {
    resetStats();
    long address = targetAddress();
    // depth 绝对值无意义(窗口从函数中途开始且 PLT 尾调用等使 CALL/RET
    // 计数不严格对称), 只断言嵌套层级体现为不同的 depth 值
    java.util.HashSet<Integer> depths = new java.util.HashSet<>();
    StalkerConfig config = new StalkerConfig().call(true).ret(true).exec(true).block(true)
        .compile(true);
    config.eventListener = events -> {
      eventCount += events.size();
      for (StalkerEvent event : events) {
        switch (event.type) {
          case CALL:
            if (event.target == innerAddress())
              sawInnerCall = true;
            depths.add(event.depth);
            break;
          case RET:
            sawRetEvent = true;
            break;
          case EXEC:
            sawExec = true;
            break;
          case BLOCK:
            sawBlockEvent = true;
            break;
          case COMPILE:
            sawCompileEvent = true;
            break;
          default:
            break;
        }
      }
    };
    StalkerFollowHandle handle = Stalker.followFunction(address, config);
    check(handle != null, "followFunction returned null");
    try {
      int result = targetFunction(5);
      check(result == expected(5), "unexpected result: " + result);
      waitEvents(10000);
      Stalker.flushEvents();
      Thread.sleep(300);
    } finally {
      handle.unhook();
    }
    check(sawInnerCall, "no inner call event");
    check(sawRetEvent, "no ret event");
    check(sawExec, "no exec event");
    check(sawBlockEvent, "no block event");
    check(sawCompileEvent, "no compile event");
    // target -> mid -> inner 至少两层调用, 嵌套 CALL 应产生不同的 depth 值
    check(depths.size() >= 2, "call depth never varied: " + depths);
  }

  /** 用例 5: callout 内读寄存器(pc 一致性/参数/SP/FP/无效名/架构差异 LR)。 */
  static void testCalloutReadRegisters() throws Exception {
    resetStats();
    long address = targetAddress();
    long inner = innerAddress();
    String argReg = firstArgRegister();
    boolean arm = argReg.equals("x0") || argReg.equals("r0");
    StalkerConfig config = new StalkerConfig().call(true);
    config.transformer = iterator -> {
      while (iterator.next()) {
        if (iterator.address() == inner) {
          iterator.putCallout(cpu -> {
            calloutCount++;
            if (cpu.getPC() != inner)
              calloutPcCorrect = false;
            // inner(n): 三次调用参数依次为 n, n+1, n-1 (target(5): 5, 6, 4)
            // 按序号读参（ABI 无关；i386 参数在栈上）
            calloutArgsLog.append(cpu.getArgument(0)).append(',');
            long sp = cpu.getSP();
            if (sp != 0)
              calloutSpSeen = true;
            if (cpu.getFP() != 0)
              calloutFpSeen = true;
            try {
              cpu.getRegister("qq9");
            } catch (IllegalArgumentException e) {
              invalidRegisterNameThrew = true;
            }
            try {
              cpu.getLR();
            } catch (IllegalArgumentException e) {
              // x86/x64 无 link register
              if (arm)
                lrBehaved = false;
            }
          });
        }
        iterator.keep();
      }
    };
    config.eventListener = events -> eventCount += events.size();
    StalkerFollowHandle handle = Stalker.followFunction(address, config);
    check(handle != null, "followFunction returned null");
    try {
      int result = targetFunction(5);
      check(result == expected(5), "unexpected result: " + result);
      waitEvents(10000);
      Stalker.flushEvents();
      Thread.sleep(300);
    } finally {
      handle.unhook();
    }
    check(calloutCount == 3, "callout count: " + calloutCount);
    check(calloutPcCorrect, "callout pc mismatch");
    check("5,6,4,".contentEquals(calloutArgsLog), "callout args: " + calloutArgsLog);
    check(calloutSpSeen, "callout sp not readable");
    check(calloutFpSeen, "callout fp not readable");
    check(invalidRegisterNameThrew, "invalid register name did not throw");
    check(lrBehaved, "getLR misbehaved on arm");
  }

  /** 用例 6: 递归函数深度计数正确性 + followFunction 多轮调用的 callout 计数。 */
  static void testRecursionAndRounds() throws Exception {
    resetStats();
    long fibAddr = fibAddress();
    check(fibAddr != 0, "fibAddress == 0");
    // 递归: 追踪窗口内正确执行, 深度计数不破坏递归
    StalkerConfig fibConfig = new StalkerConfig().call(true).ret(true);
    fibConfig.eventListener = events -> eventCount += events.size();
    StalkerFollowHandle fibHandle = Stalker.followFunction(fibAddr, fibConfig);
    check(fibHandle != null, "followFunction(fib) returned null");
    try {
      int fib10 = fib(10);
      check(fib10 == 55, "fib(10) under stalker: " + fib10);
      waitEvents(10000);
      Stalker.flushEvents();
      Thread.sleep(300);
    } finally {
      fibHandle.unhook();
    }
    check(eventCount > 0, "no events from recursive function");

    // 多轮: 挂着 hook 连续三轮调用, 每轮 inner 三个入参点都触发 callout
    resetStats();
    long address = targetAddress();
    long inner = innerAddress();
    StalkerConfig config = new StalkerConfig().call(true);
    config.transformer = iterator -> {
      while (iterator.next()) {
        if (iterator.address() == inner) {
          iterator.putCallout(cpu -> calloutCount++);
        }
        iterator.keep();
      }
    };
    config.eventListener = events -> eventCount += events.size();
    StalkerFollowHandle handle = Stalker.followFunction(address, config);
    check(handle != null, "followFunction returned null");
    try {
      check(targetFunction(1) == expected(1), "round 1 result");
      check(targetFunction(2) == expected(2), "round 2 result");
      check(targetFunction(3) == expected(3), "round 3 result");
      waitEvents(10000);
      Stalker.flushEvents();
      Thread.sleep(300);
    } finally {
      handle.unhook();
    }
    check(calloutCount == 9, "callout fired " + calloutCount + " times, expected 9");
    check(eventCount > 0, "no events for 3 rounds");
  }

  /** 用例 7: followSymbol 符号查址追踪 + 辅助 API 冒烟 + 幂等 unhook + null listener。 */
  static void testFollowSymbolAndUtilities() throws Exception {
    // 符号查址: JNI 导出符号按名追踪
    resetStats();
    long inner = innerAddress();
    StalkerConfig config = new StalkerConfig().call(true);
    config.eventListener = events -> {
      eventCount += events.size();
      for (StalkerEvent event : events) {
        if (event.type == StalkerEvent.Type.CALL && event.target == inner)
          sawInnerCall = true;
      }
    };
    StalkerFollowHandle handle = Stalker.followSymbol("api",
        "Java_qing_albatross_demo_StalkerTest_targetFunction", config);
    check(handle != null, "followSymbol returned null");
    try {
      int result = targetFunction(5);
      check(result == expected(5), "followSymbol result: " + result);
      waitEvents(10000);
      Stalker.flushEvents();
      Thread.sleep(300);
    } finally {
      handle.unhook();
      handle.unhook(); // 幂等
    }
    check(sawInnerCall, "no inner call event via followSymbol");
    check(eventCount > 0, "no events via followSymbol");

    // 辅助 API 冒烟: 不追踪时调用不应崩溃
    Stalker.setTrustThreshold(1);
    Stalker.invalidate(inner);
    Stalker.garbageCollect();
    Stalker.flushEvents();

    // null listener + null transformer: 会话无监听, 行为不受影响
    resetStats();
    StalkerConfig quiet = new StalkerConfig().call(true);
    StalkerFollowHandle quietHandle = Stalker.followFunction(targetAddress(), quiet);
    check(quietHandle != null, "followFunction(quiet) returned null");
    try {
      check(targetFunction(5) == expected(5), "quiet session result");
      Thread.sleep(200);
    } finally {
      quietHandle.unhook();
    }
  }

  /** 用例 8: includeModule 白名单只追踪指定 so, exclude 可在集合内挖洞。 */
  static void testIncludeModule() throws Exception {
    String arch = System.getProperty("os.arch", "");
    if (arch.contains("arm") && !arch.contains("aarch64") && !arch.contains("arm64")) {
      // arm32 后端未实现过滤判定, include/exclude 都是 no-op
      throw TestRunner.skip("arm32 后端不支持 include/exclude 过滤");
    }
    long address = targetAddress();
    long inner = innerAddress();

    // 阶段 A: 只追踪 demo 自己的 so, 目标链(impl->mid->inner)正常翻译
    // 坑见 doc/pitfalls/pitfall-java-suite-duration-20261010.md（回调别攒列表 + 会话必须显式设窗口）
    resetStats();
    Stalker.includeModule("api");
    // 显式短窗口: 不设就是 60s 兜底, 一旦出事(事件洪水/断言失败)整类要多耗一分钟
    StalkerConfig config = new StalkerConfig().call(true).exec(true)
        .timeout(StressSoak.ms(2000, 60000));
    config.eventListener = events -> {
      eventCount += events.size();
      for (StalkerEvent event : events) {
        if (event.type == StalkerEvent.Type.EXEC) {
          execEventCount++;
          long location = event.location;
          if (location >= inner && location < inner + 16)
            sawExecInInner = true;
        } else if (event.type == StalkerEvent.Type.CALL && event.target == inner) {
          sawInnerCall = true;
        }
      }
    };
    StalkerFollowHandle handle = Stalker.followFunction(address, config);
    check(handle != null, "followFunction returned null");
    try {
      int result = targetFunction(5);
      check(result == expected(5), "includeModule result: " + result);
      waitEvents(StressSoak.ms(2000, 10000));
      drainEvents();
    } finally {
      handle.unhook();
    }
    Albatross.log("includeModule A: events=" + eventCount + " exec=" + execEventCount
        + " execInInner=" + sawExecInInner + " innerCall=" + sawInnerCall);
    check(sawInnerCall, "no inner call event with includeModule");
    check(eventCount > 0, "no events with includeModule");
    check(sawExecInInner, "inner body not traced under include");

    // 阶段 B: 保持 include 不变, 再把 inner 挖出去: 其调用转原生执行,
    // 函数行为不变但不再产生 inner 内的 EXEC 事件
    drainEvents(); /* 先排空上一阶段的投递积压, 别让它落进本阶段的断言窗口 */
    resetStats();
    Stalker.exclude(inner, 16);
    StalkerFollowHandle handleB = Stalker.followFunction(address, config);
    check(handleB != null, "followFunction(phase B) returned null");
    try {
      int result = targetFunction(5);
      check(result == expected(5), "include+exclude result: " + result);
      waitEvents(StressSoak.ms(2000, 10000));
      drainEvents();
    } finally {
      handleB.unhook();
      // 过滤表是会话级全局配置, 清理避免影响后续用例
      Stalker.clearInclusions();
      Stalker.clearExclusions();
    }
    Albatross.log("includeModule B: events=" + eventCount + " exec=" + execEventCount
        + " execInInner=" + sawExecInInner);
    check(eventCount > 0, "no events with include+exclude");
    check(!sawExecInInner, "excluded inner still traced");
  }

  /** 把投递队列排空(会话停后仍可能有在途事件), 供跨阶段的断言切换用。 */
  static void drainEvents() throws InterruptedException {
    Stalker.flushEvents();
    Thread.sleep(300);
  }
}
