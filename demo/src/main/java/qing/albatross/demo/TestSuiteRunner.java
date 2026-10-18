package qing.albatross.demo;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;

import qing.albatross.core.Albatross;

/**
 * 测试类展开与逐类执行：APK 广播运行器（{@link AlbatrossDemoMainActivity}）与
 * 原生宿主（{@link JavaTestRunner}）共用的一份实现，保证两条链路的
 * RUN_TEST 上报行完全一致（run_apk_tests.sh / run_java_tests.sh 都按它解析）。
 *
 * <p>上报统一走 {@link TestRunner}（tag AlbatrossDemo；原生宿主再镜像到 stdout）。
 */
public final class TestSuiteRunner {

  public static final String TAG = "AlbatrossDemo";
  public static final String DEMO_PACKAGE = "qing.albatross.demo";

  private TestSuiteRunner() {
  }

  /** 一次运行的汇总（与广播运行器的口径一致：skip 不算失败）。 */
  public static final class Summary {
    public int passed;
    public int failed;
    public int skipped;
    public long elapsedMs;

    public boolean hasFailure() {
      return failed > 0;
    }
  }

  /**
   * 展开请求的测试类：单类、逗号分隔多类、或 all(dex 扫描发现)。
   * all 模式排除类名以数字结尾的单用例薄壳(如 StalkerTest1-7)，避免与其完整版重复执行。
   */
  public static List<String> expandTestClasses(String requestedClassName, ClassLoader loader) {
    if (requestedClassName != null && requestedClassName.trim().equals("all")) {
      return discoverTestClasses(loader);
    }
    List<String> classNames = new ArrayList<>();
    if (requestedClassName != null) {
      for (String name : requestedClassName.split(",")) {
        classNames.add(resolveTestClassName(name));
      }
    } else {
      classNames.add(resolveTestClassName(null));
    }
    return classNames;
  }

  /** 枚举 demo 包内可执行的测试类: *Test 结尾、非数字结尾薄壳、含 static test()。 */
  public static List<String> discoverTestClasses(ClassLoader loader) {
    List<String> classNames = new ArrayList<>();
    try {
      Class<?> baseDexClassLoader = Class.forName("dalvik.system.BaseDexClassLoader");
      Field pathListField = baseDexClassLoader.getDeclaredField("pathList");
      pathListField.setAccessible(true);
      Object pathList = pathListField.get(loader);
      Field dexElementsField = pathList.getClass().getDeclaredField("dexElements");
      dexElementsField.setAccessible(true);
      Object[] dexElements = (Object[]) dexElementsField.get(pathList);
      for (Object element : dexElements) {
        Field dexFileField = element.getClass().getDeclaredField("dexFile");
        dexFileField.setAccessible(true);
        Object dexFile = dexFileField.get(element);
        if (dexFile == null) {
          continue;
        }
        Method entriesMethod = dexFile.getClass().getMethod("entries");
        Enumeration<String> entries = (Enumeration<String>) entriesMethod.invoke(dexFile);
        while (entries.hasMoreElements()) {
          String className = entries.nextElement();
          if (!className.startsWith(DEMO_PACKAGE + ".")) {
            continue;
          }
          String simple = className.substring(DEMO_PACKAGE.length() + 1);
          if (simple.indexOf('.') >= 0 || !simple.endsWith("Test")
              || Character.isDigit(simple.charAt(simple.length() - 1))) {
            continue;
          }
          if (!hasStaticTestMethod(className, loader)) {
            continue;
          }
          if (!classNames.contains(className)) {
            classNames.add(className);
          }
        }
      }
    } catch (Throwable e) {
      Albatross.log("discover test classes fail", e);
    }
    Collections.sort(classNames);
    if (classNames.isEmpty()) {
      throw new IllegalStateException("no test classes discovered");
    }
    return classNames;
  }

  public static String resolveTestClassName(String requestedClassName) {
    if (requestedClassName == null || requestedClassName.trim().isEmpty()) {
      throw new IllegalArgumentException("missing test class name");
    }
    String className = requestedClassName.trim();
    if (className.indexOf('.') < 0) {
      className = DEMO_PACKAGE + "." + className;
    }
    if (!className.startsWith(DEMO_PACKAGE + ".")) {
      throw new SecurityException("test class must be inside " + DEMO_PACKAGE);
    }
    return className;
  }

  public static boolean hasStaticTestMethod(String className, ClassLoader loader) {
    try {
      Class<?> testClass = Class.forName(className, false, loader);
      Method testMethod = testClass.getDeclaredMethod("test");
      return Modifier.isStatic(testMethod.getModifiers());
    } catch (Throwable e) {
      return false;
    }
  }

  /** 执行单个测试类并上报(running/end/success|skipped|failed)。 */
  public static TestRunner.Status invokeTest(String className, int index, int total, ClassLoader loader) {
    TestRunner.beginClass(className, index, total);
    Throwable thrown = null;
    try {
      Class<?> testClass = Class.forName(className, false, loader);
      Method testMethod = testClass.getDeclaredMethod("test");
      if (!Modifier.isStatic(testMethod.getModifiers())) {
        throw new IllegalArgumentException(className + ".test() must be static");
      }
      testMethod.setAccessible(true);
      testMethod.invoke(null);
    } catch (InvocationTargetException error) {
      thrown = error.getCause() == null ? error : error.getCause();
    } catch (Throwable error) {
      thrown = error;
    }
    TestRunner.ClassResult result = TestRunner.endClass(thrown);
    TestRunner.report("RUN_TEST end: " + className + " status=" + statusName(result.status)
        + " ms=" + result.elapsedMs);
    switch (result.status) {
      case OK:
        TestRunner.report("RUN_TEST success: " + className);
        break;
      case SKIP:
        TestRunner.report("RUN_TEST skipped: " + className + ": " + result.skipReason);
        break;
      default:
        if (thrown != null) {
          TestRunner.reportError("RUN_TEST failed: " + className + ": " + thrown, thrown);
        } else {
          TestRunner.report("RUN_TEST failed: " + className + ": " + failureDetail(className, result));
        }
        break;
    }
    return result.status;
  }

  /** 依次执行一组测试类，结束时打 RUN_TEST summary 行并返回汇总。 */
  public static Summary runAll(List<String> classNames, ClassLoader loader) {
    Summary summary = new Summary();
    long startNs = System.nanoTime();
    for (int i = 0; i < classNames.size(); i++) {
      TestRunner.Status status = invokeTest(classNames.get(i), i + 1, classNames.size(), loader);
      if (status == TestRunner.Status.OK) {
        summary.passed++;
      } else if (status == TestRunner.Status.SKIP) {
        summary.skipped++;
      } else {
        summary.failed++;
      }
    }
    summary.elapsedMs = (System.nanoTime() - startNs) / 1_000_000L;
    TestRunner.logSummary(summary.passed, summary.failed, summary.skipped, summary.elapsedMs);
    return summary;
  }

  public static String statusName(TestRunner.Status status) {
    switch (status) {
      case OK:
        return "ok";
      case SKIP:
        return "skip";
      default:
        return "fail";
    }
  }

  /** 类级失败摘要: 失败用例相对类名列出, 最多 3 个。 */
  public static String failureDetail(String className, TestRunner.ClassResult result) {
    StringBuilder detail = new StringBuilder();
    detail.append(result.failedCases.size()).append(" 个步骤失败: ");
    int shown = 0;
    for (String path : result.failedCases) {
      if (shown == 3) {
        detail.append(" 等");
        break;
      }
      if (shown > 0) {
        detail.append(", ");
      }
      String prefix = className + "/";
      detail.append(path.startsWith(prefix) ? path.substring(prefix.length()) : path);
      shown++;
    }
    return detail.toString();
  }
}
