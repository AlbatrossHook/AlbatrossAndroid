package qing.albatross.demo;

import android.util.Log;

import java.util.ArrayList;
import java.util.List;

/**
 * demo 测试的步骤级上报器: 计时、成功/失败/跳过、类作用域与嵌套作用域。
 *
 * <p>所有上报都走 {@link #TAG} 这一个 tag, run_apk_tests.sh 靠它抓取进度;
 * 不能用 Albatross.log(tag "Albatross"), 脚本看不到。
 *
 * <p>用法: 广播运行器对每个测试类调用 {@link #beginClass}/{@link #endClass};
 * 聚合测试内部用 {@link #run(String, Step)} 包住每个子用例。子用例失败后继续
 * 执行剩余用例, 失败在类结束时汇总; 抛 {@link SkipException} 表示该用例(或
 * 整个类, 若直接抛出 test())不适用, 不计为失败。
 */
public final class TestRunner {

  /** 广播 extra `stage` 透传（探针类用；-1=未指定）。 */
  public static volatile int stageOverride = -1;


  public static final String TAG = "AlbatrossDemo";

  /** 一个命名用例的入口。 */
  public interface Step {
    void run() throws Throwable;
  }

  /** 用例/整类跳过: 不计失败, 上报为 skip。在 test() 里直接抛出即为整类 SKIP。 */
  public static class SkipException extends RuntimeException {
    public SkipException(String reason) {
      super(reason);
    }
  }

  /** 进度回调(界面显示用), 可能来自测试线程。 */
  public interface ProgressListener {
    void onProgress(String line);
  }

  public enum Status { OK, FAIL, SKIP }

  public static final class ClassResult {
    public final Status status;
    public final long elapsedMs;
    /** 失败用例的路径列表(status=FAIL 时非空)。 */
    public final List<String> failedCases;
    /** 跳过原因(status=SKIP 时非空)。 */
    public final String skipReason;

    ClassResult(Status status, long elapsedMs, List<String> failedCases, String skipReason) {
      this.status = status;
      this.elapsedMs = elapsedMs;
      this.failedCases = failedCases;
      this.skipReason = skipReason;
    }
  }

  private static final class Failure {
    final String path;
    final String message;

    Failure(String path, String message) {
      this.path = path;
      this.message = message;
    }
  }

  /** 一个作用域: 根作用域是一次类运行, 嵌套作用域是一个子用例。 */
  private static final class Scope {
    final String path;
    final long startNs = System.nanoTime();
    final List<Failure> failures = new ArrayList<>();
    String skipReason;
    /** 本次运行内已结束的用例计数(含分组用例), 用于界面显示与自管理运行的汇总。 */
    int okCases;
    int failedCases;
    int skippedCases;

    Scope(String path) {
      this.path = path;
    }

    long elapsedMs() {
      return (System.nanoTime() - startNs) / 1_000_000L;
    }
  }

  /** 测试线程内的作用域栈; 用 ThreadLocal 避免并行测试互相污染。 */
  private static final ThreadLocal<ArrayList<Scope>> SCOPES =
      new ThreadLocal<ArrayList<Scope>>() {
        @Override
        protected ArrayList<Scope> initialValue() {
          return new ArrayList<>();
        }
      };

  private static volatile ProgressListener progressListener;
  /** 最近一次(任意线程)上报后的进度快照, 供界面线程读取。 */
  private static volatile String lastSnapshot = "";
  /** stdout 镜像(原生宿主 albatross_test --java 用), 默认关闭。 */
  private static volatile java.io.PrintStream outputStream;

  private TestRunner() {
  }

  public static void setProgressListener(ProgressListener listener) {
    progressListener = listener;
  }

  /** 广播运行器在测试类开始前调用: 开根作用域并打印 running 行。 */
  public static void beginClass(String className, int index, int total) {
    ArrayList<Scope> stack = SCOPES.get();
    stack.clear();
    stack.add(new Scope(className));
    log("RUN_TEST running: " + className + " (" + index + "/" + total + ")");
  }

  /** 广播运行器在测试类结束时调用: 关根作用域, 汇总类级结果。 */
  public static ClassResult endClass(Throwable thrown) {
    ArrayList<Scope> stack = SCOPES.get();
    if (stack.isEmpty()) {
      String message = thrown == null ? null : describe(thrown);
      if (thrown instanceof SkipException) {
        return new ClassResult(Status.SKIP, 0, new ArrayList<String>(), message);
      }
      if (thrown != null) {
        List<String> failed = new ArrayList<>();
        failed.add(message);
        return new ClassResult(Status.FAIL, 0, failed, null);
      }
      return new ClassResult(Status.OK, 0, new ArrayList<String>(), null);
    }
    ClassResult result = closeRoot(thrown);
    stack.clear();
    return result;
  }

  private static ClassResult closeRoot(Throwable thrown) {
    Scope root = SCOPES.get().get(0);
    if (thrown != null) {
      if (thrown instanceof SkipException) {
        if (root.skipReason == null) {
          root.skipReason = describe(thrown);
        }
      } else {
        root.failures.add(new Failure(root.path, describe(thrown)));
      }
    }
    List<String> names = new ArrayList<>();
    for (Failure failure : root.failures) {
      names.add(failure.path);
    }
    Status status;
    if (!root.failures.isEmpty()) {
      status = Status.FAIL;
    } else if (root.skipReason != null) {
      status = Status.SKIP;
    } else {
      status = Status.OK;
    }
    return new ClassResult(status, root.elapsedMs(), names, root.skipReason);
  }

  /** 界面显示用的最近一次运行进度: {@code <作用域> ok=N fail=N skip=N}。 */
  public static String snapshot() {
    return lastSnapshot;
  }

  private static String currentSnapshot() {
    ArrayList<Scope> stack = SCOPES.get();
    if (stack.isEmpty()) {
      return "";
    }
    Scope root = stack.get(0);
    return root.path + " ok=" + root.okCases + " fail=" + root.failedCases
        + " skip=" + root.skippedCases;
  }

  /** 执行一个命名用例: 计时并上报 begin/ok/fail/skip, 失败后继续后续用例。 */
  public static void run(String name, Step body) {
    ArrayList<Scope> stack = SCOPES.get();
    boolean adhocRoot = false;
    if (stack.isEmpty()) {
      // 界面按钮等非广播路径: 没有 beginClass, 按调用方类名兜一个根作用域
      stack.add(new Scope(callerClassName()));
      adhocRoot = true;
    }
    Scope root = stack.get(0);
    Scope parent = stack.get(stack.size() - 1);
    Scope scope = new Scope(parent.path + "/" + name);
    stack.add(scope);
    log("RUN_TEST case: " + root.path + " begin " + relative(root, scope.path));
    try {
      body.run();
    } catch (SkipException skip) {
      scope.skipReason = describe(skip);
    } catch (Throwable error) {
      scope.failures.add(new Failure(scope.path, describe(error)));
    } finally {
      stack.remove(stack.size() - 1);
    }
    long elapsedMs = scope.elapsedMs();
    if (scope.skipReason != null) {
      root.skippedCases++;
      log("RUN_TEST case: " + root.path + " skip " + relative(root, scope.path)
          + " ms=" + elapsedMs + " reason=" + scope.skipReason);
    } else if (!scope.failures.isEmpty()) {
      root.failedCases++;
      Failure failure = scope.failures.get(0);
      logErr("RUN_TEST case: " + root.path + " fail " + relative(root, scope.path)
          + " ms=" + elapsedMs + " err=" + failure.message);
    } else {
      root.okCases++;
      log("RUN_TEST case: " + root.path + " ok " + relative(root, scope.path) + " ms=" + elapsedMs);
    }
    parent.failures.addAll(scope.failures);
    if (adhocRoot) {
      // 自管理运行(界面路径): 关作用域并打汇总, 避免状态残留到下一次运行
      ClassResult result = closeRoot(null);
      logSummary(root.okCases, root.failedCases, root.skippedCases, result.elapsedMs);
      stack.clear();
    }
  }

  /** 整类/用例跳过的语法糖: {@code throw TestRunner.skip("...")}。 */
  public static SkipException skip(String reason) {
    return new SkipException(reason);
  }

  /** 本次运行的类级汇总行。 */
  public static void logSummary(int passed, int failed, int skipped, long elapsedMs) {
    log("RUN_TEST summary: passed=" + passed + " failed=" + failed + " skipped=" + skipped
        + " total=" + (passed + failed + skipped) + " ms=" + elapsedMs);
  }

  private static String relative(Scope root, String path) {
    if (path.startsWith(root.path + "/")) {
      return path.substring(root.path.length() + 1);
    }
    return path;
  }

  private static String describe(Throwable error) {
    String message = error.getMessage();
    if (message == null || message.isEmpty()) {
      return error.getClass().getSimpleName();
    }
    return error.getClass().getSimpleName() + ": " + message;
  }

  /** 未显式 beginClass 时(如界面按钮直接调用测试)按调用方类名兜一个根作用域。 */
  private static String callerClassName() {
    String self = TestRunner.class.getName();
    for (StackTraceElement frame : Thread.currentThread().getStackTrace()) {
      String name = frame.getClassName();
      if (name.equals(self) || name.equals(Thread.class.getName())) {
        continue;
      }
      int lambda = name.indexOf("$$");
      if (lambda < 0) {
        lambda = name.indexOf("-$$Lambda");
      }
      return lambda > 0 ? name.substring(0, lambda) : name;
    }
    return "test";
  }

  private static void log(String line) {
    publish(line, false, null);
  }

  private static void logErr(String line) {
    publish(line, true, null);
  }

  /**
   * 通用上报行（类级/运行级状态）：与子用例同一通路，Log(tag AlbatrossDemo)
   * + stdout 镜像 + 进度回调。
   */
  public static void report(String line) {
    publish(line, false, null);
  }

  /** 同 report，附带异常（logcat 里能看到堆栈）。 */
  public static void reportError(String line, Throwable error) {
    publish(line, true, error);
  }

  /**
   * stdout 镜像：原生宿主（albatross_test --java）设成 System.out，脚本直接抓
   * stdout 就能拿到 RUN_TEST 行；APK 广播路径不设置，行为不变。
   */
  public static void setOutputStream(java.io.PrintStream stream) {
    outputStream = stream;
  }

  private static void publish(String line, boolean error, Throwable throwable) {
    lastSnapshot = currentSnapshot();
    if (error) {
      if (throwable != null) {
        Log.e(TAG, line, throwable);
      } else {
        Log.e(TAG, line);
      }
    } else {
      Log.i(TAG, line);
    }
    java.io.PrintStream stream = outputStream;
    if (stream != null) {
      stream.println(line);
      stream.flush();
    }
    ProgressListener listener = progressListener;
    if (listener != null) {
      listener.onProgress(line);
    }
  }
}
