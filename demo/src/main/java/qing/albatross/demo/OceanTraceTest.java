package qing.albatross.demo;

import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicInteger;

import albatross.demo.OceanTraceTarget;
import qing.albatross.core.Albatross;
import qing.albatross.tracker.EventType;
import qing.albatross.tracker.KeyWordSinker;
import qing.albatross.tracker.MethodCallContext;
import qing.albatross.tracker.OceanLogger;
import qing.albatross.tracker.OceanSinker;
import qing.albatross.tracker.OceanTracker;
import qing.albatross.tracker.StringSinker;

/**
 * OceanLogger.log 可插拔替换 + OceanTracker sinker 路由/包过滤验证:
 *
 * 用例 1 loggerReplace: OceanLogger.log 被替换后, 直调必须进替换方法(行为断言,
 *       不依赖 unhook/re-replace 这些状态敏感路径)——钉住"替换是否真的生效"
 *       (2026-10-05 改判 bug-ocean-logger-replace-ineffective-realdevice-20261004
 *       时缺的正是这个判据)。
 * 用例 2 keywordSinker: KeyWordSinker(trace_keyword 路径)的命中行经 OceanLogger.log 输出
 *       (java 仓 4da9da9 修复回归: 此前写死 Albatross.log, FLAG_LOG 下永不落盘);
 *       未命中关键词不输出。
 * 用例 3 stringSinker: StringSinker(trace_string 路径)同上。
 * 用例 4 whitePackage: 白名单包前缀内方法不再派发 CALL 事件, 移除后恢复。
 *
 * 生命周期: 整个类只在入口 replace 一次(OceanLogger.log -> recorder), 各用例共享;
 * 框架契约是"同一目标同时只挂一个 hook", 已 hook 时 replace 返回 false(HOOK_ALREADY)、
 * 卸载(unhookMethod)对单 replace-hook 链可能返回 NOT_HOOK——这些状态敏感路径不进断言,
 * 类收尾 best-effort registerLogger() 恢复默认注册。
 *
 * 注意: recorder 只做字段写入、不调任何容器/JDK 方法——keyword 窗口内它由 sinker 调用,
 * 若在窗口里触发被追踪的 JDK 调用(参数含命中串)会被 sinker 再命中, 无限递归。
 * 靶子在 {@link OceanTraceTarget}(qing.albatross. 白名单之外), 断言类本身在白名单内,
 * 事件只可能来自靶子。
 */
public class OceanTraceTest {

  /** recorder 收到的最近一行与次数; volatile 字段写入, 不经任何方法调用 */
  static volatile String lastLogged;
  static volatile int loggerHits;
  static boolean loggerHooked;

  static Method loggerLog;
  static Method recorderMethod;

  static void recorder(String msg) {
    loggerHits++;
    lastLogged = msg;
  }

  static final class CountingSinker extends OceanSinker {
    final AtomicInteger calls = new AtomicInteger();

    @Override
    public void call(MethodCallContext context, Member member) {
      calls.incrementAndGet();
    }
  }

  static void check(boolean cond, String message) {
    if (!cond)
      throw new AssertionError(message);
  }

  static void checkLoggerHooked() {
    if (!loggerHooked)
      throw TestRunner.skip("OceanLogger.log 未被本类 replace 占用(进程内已有其它注册)");
  }

  public static void test() throws Exception {
    if (!OceanTracker.init(0))
      throw new IllegalStateException("OceanTracker.init(0) failed");
    loggerLog = OceanLogger.class.getDeclaredMethod("log", String.class);
    recorderMethod = OceanTraceTest.class.getDeclaredMethod("recorder", String.class);
    loggerHooked = Albatross.replace(loggerLog, recorderMethod);
    TestRunner.run("loggerReplace", OceanTraceTest::testLoggerReplace);
    TestRunner.run("keywordSinker", OceanTraceTest::testKeywordSinker);
    TestRunner.run("stringSinker", OceanTraceTest::testStringSinker);
    TestRunner.run("whitePackage", OceanTraceTest::testWhitePackage);
    /* 收尾 best-effort: 恢复默认注册(OceanLogger.log -> Albatross.log); 已 hook 时
     * replace 可能返回 false, 不影响本套件判定(进程随用例结束销毁) */
    OceanLogger.registerLogger();
  }

  /** 用例 1: 替换后直调 OceanLogger.log 必须进替换方法(含参数透传) */
  static void testLoggerReplace() throws Exception {
    checkLoggerHooked();
    OceanLogger.log("ocean-replace-mark-1");
    check(loggerHits >= 1 && "ocean-replace-mark-1".equals(lastLogged),
        "OceanLogger.log 未走替换方法: hits=" + loggerHits + " last=" + lastLogged);
    OceanLogger.log("ocean-replace-mark-2");
    check(loggerHits >= 2 && "ocean-replace-mark-2".equals(lastLogged),
        "第二次调用未走替换方法: hits=" + loggerHits + " last=" + lastLogged);
  }

  /** 用例 2: keyword 命中行经 OceanLogger.log 输出(4da9da9 回归); 未命中不输出 */
  static void testKeywordSinker() throws Exception {
    checkLoggerHooked();
    check(OceanTracker.traceMe(new KeyWordSinker("OCEANNEEDLE"), EventType.CALL),
        "traceMe(KeyWordSinker) failed");
    try {
      /* 已编译方法要拉回解释器才会派发事件(同 TraceTimeoutTest 口径) */
      OceanTracker.syncTrace();
      OceanTraceTarget.keywordTarget("OCEANNEEDLE");
      check(loggerHits >= 1 && lastLogged != null && lastLogged.contains("keywordTarget"),
          "keyword 命中行未进 OceanLogger.log: hits=" + loggerHits + " last=" + lastLogged);
      String keep = lastLogged;
      OceanTraceTarget.keywordTarget("plain-argument");
      check(lastLogged == keep, "未命中关键词也产出了行: last=" + lastLogged);
    } finally {
      if (OceanTracker.isCurrentThreadTraced())
        OceanTracker.stopTraceMe();
    }
  }

  /** 用例 3: StringSinker(String 参数按小写包含匹配)命中行同样经 OceanLogger.log 输出 */
  static void testStringSinker() throws Exception {
    checkLoggerHooked();
    check(OceanTracker.traceMe(new StringSinker("needlestr"), EventType.CALL),
        "traceMe(StringSinker) failed");
    try {
      OceanTracker.syncTrace();
      OceanTraceTarget.stringTarget("xxNeedleStrYY");
      check(loggerHits >= 1 && lastLogged != null && lastLogged.contains("stringTarget"),
          "string 命中行未进 OceanLogger.log: hits=" + loggerHits + " last=" + lastLogged);
    } finally {
      if (OceanTracker.isCurrentThreadTraced())
        OceanTracker.stopTraceMe();
    }
  }

  /** 用例 4: 白名单包前缀内方法不派发 CALL 事件, removeWhitePackage 后恢复。
   *  白名单过滤只在 trace scope 含 kPackageFilter 时生效(默认 kAll 不含), 用例内显式设置并恢复 */
  static void testWhitePackage() throws Exception {
    OceanTracker.setTraceScope(OceanTracker.kApplication | OceanTracker.kPackageFilter);
    try {
      CountingSinker sinker = new CountingSinker();
      /* 基线: 未加白名单时靶子包的事件可达 sinker(事件源是 spin 内层的 bump) */
      check(OceanTracker.traceMe(sinker, EventType.CALL), "traceMe baseline failed");
      try {
        OceanTracker.syncTrace();
        OceanTraceTarget.spin(5000);
      } finally {
        if (OceanTracker.isCurrentThreadTraced())
          OceanTracker.stopTraceMe();
      }
      check(sinker.calls.get() >= 1000,
          "baseline 未收到靶子包事件: " + sinker.calls.get());

      /* 加入白名单: 先 drain, 再断言——存量已插桩方法(bump)允许极少量残留
       * (实测 2~3/5000, 来自加白前的插桩状态), 全新方法必须严格为 0 */
      check(OceanTracker.addWhitePackage("albatross.demo."), "addWhitePackage failed");
      try {
        sinker.calls.set(0);
        check(OceanTracker.traceMe(sinker, EventType.CALL), "traceMe whitelisted failed");
        try {
          OceanTracker.syncTrace();
          OceanTraceTarget.spin(5000);
          check(sinker.calls.get() < 100,
              "白名单未生效(派发 " + sinker.calls.get() + " / 5000)");
          sinker.calls.set(0);
          /* keywordTarget 此前从未被插桩, 首次被考虑即应被白名单拦截 */
          OceanTraceTarget.keywordTarget("white-check");
          check(sinker.calls.get() == 0,
              "白名单对全新方法仍派发了事件: " + sinker.calls.get());
        } finally {
          if (OceanTracker.isCurrentThreadTraced())
            OceanTracker.stopTraceMe();
        }
      } finally {
        check(OceanTracker.removeWhitePackage("albatross.demo."), "removeWhitePackage failed");
      }

      /* 移除后恢复派发 */
      sinker.calls.set(0);
      check(OceanTracker.traceMe(sinker, EventType.CALL), "traceMe restored failed");
      try {
        OceanTracker.syncTrace();
        OceanTraceTarget.spin(5000);
      } finally {
        if (OceanTracker.isCurrentThreadTraced())
          OceanTracker.stopTraceMe();
      }
      check(sinker.calls.get() >= 1000,
          "移除白名单后未恢复派发: " + sinker.calls.get());
    } finally {
      /* 恢复默认 scope(kAll = kJdk|kPlatform|kApplication) */
      OceanTracker.setTraceScope(OceanTracker.kJdk | OceanTracker.kPlatform
          | OceanTracker.kApplication);
    }
  }
}
