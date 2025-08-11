package qing.albatross.demo;

import android.os.Build;
import android.os.Debug;

import java.io.PrintStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import qing.albatross.annotation.ConstructorBackup;
import qing.albatross.annotation.ConstructorHook;
import qing.albatross.annotation.FieldRef;
import qing.albatross.annotation.MethodBackup;
import qing.albatross.annotation.MethodHook;
import qing.albatross.annotation.TargetClass;
import qing.albatross.core.Albatross;
import qing.albatross.exception.AlbatrossException;

public class TestMain {


  public static int i;
  private static final int d = 7;

  static class A {
    public long a() {
      System.out.println("A:a called");
      return 1;
    }
  }

  static class B extends A {
    @Override
    public long a() {
      System.out.println("B:a called");
      return 2;
    }
  }

  static class A2 {
    public long a() {
      System.out.println("A2:a called");
      return 1;
    }
  }

  static class B2 extends A2 {
    @Override
    public long a() {
      System.out.println("B2:a called");
      return 2;
    }
  }

  static class B3 {
    public int b = 11114;
    public int i = 2;
  }

  @TargetClass(B3.class)
  static class B3H {
    static int i_fake = 1111;
    @FieldRef
    int i = 4;
  }


  public static int testIntReturn() throws Exception {
    throw new Exception("throw test return");
  }


  @TargetClass(TestMain.class)
  static class TestMainH {
    @FieldRef
    public static int i = 5;
    @FieldRef
    public static int d = 9;

//    static {
//      assert Albatross.hookClass() >= 2;
//    }

    @FieldRef
    public int z;

    @MethodBackup
    @MethodHook
    private void testCall(int i) {
      testCall(i + 2);
      assert this.z == i + 2;
    }


    @ConstructorBackup
    @ConstructorHook
    static void init(TestMain testMain, int z, int b) {
//      if (initCount.getAndIncrement() > 612) {
//        String msg = "TestMainH init can not call backup method:" + initCount + " z:" + z + " b:" + b;
//        Albatross.log(msg);
//        throw new RuntimeException(msg);
//      }
      init(testMain, z + 1, b);
    }

    //    @MethodBackup
    static native long testCall2(TestMainH testMain, long z);

    @MethodBackup
    private native long testCall2(long z);

    @MethodHook
    long testCall2$Hook(long z) {
//      return testCall2(this,z + 1);
      return testCall2(z + 1);
    }
  }

  public static void testAField(int i) {
    TestMainH.i = i + 1;
    if (TestMain.i != i + 1) {
      Albatross.log("TestMainH i:" + TestMainH.i + " d:" + TestMainH.d + " expect:" + (i + 1));
      Albatross.log("TestMain i:" + TestMain.i + " d:" + TestMain.d + " expect:" + (i + 1));
      assert TestMain.i == i + 1;
    }
    TestMain.i = i;
    if (TestMainH.i != i) {
      Albatross.log("i:" + TestMainH.i + " d:" + TestMainH.d + " expect:" + (i));
      assert TestMainH.i == i;
    }
  }

  public static void main(boolean hook) throws Exception {
    if (Debug.isDebuggerConnected()) {
      System.out.println("debugger isDebuggerConnected");
    }
    Albatross.loadLibrary(null);
    PrimCoarseMatch.test(hook);
    BinderHook.test(hook);
    FieldRefTest.test(hook);
    DisableMethodTest.test(hook);
    RequiredTest.test(hook);
    HookerStructErrTest.test(hook);
    ClassInfer.test(hook);
    ApiTest.test(hook);
    TargetHookTest.main(hook);
    MethodDefTest.test(hook);
    try {
      ActivityHook.test(hook);
      int level = Albatross.transactionBegin();
      assert level == 1;
      assert Albatross.hookClass(TestMainH.class) == Albatross.CLASS_ALREADY_HOOK;
      boolean canCreate = false;
      try {
        new TestMainH();
        canCreate = true;
      } catch (Throwable e) {
      }
      if (canCreate) {
        Albatross.log("it should can not create hooker");
      } else {
        Albatross.log("Work OK!Can not create hooker");
      }
      level = Albatross.transactionEnd(true);
      assert level == 0;
      Constructor<TestMain> declaredConstructor = TestMain.class.getDeclaredConstructor(int.class, int.class);
      Albatross.log("call TestMain create " + Albatross.entryPointFromQuickCompiledCode(declaredConstructor));
      TestMain a = new TestMain(1, 1);
      if (a.z != 2) {
        Albatross.log("wrong field value");
        Albatross.entryPointFromQuickCompiledCode(declaredConstructor);
        assert a.z == 2;
      } else {
        Albatross.log("test TestMain constructor ok");
      }
    } catch (Exception e) {
      e.printStackTrace();
    }
    if (Albatross.isFieldEnable()) {
      Albatross.log("try field test");
      FieldTest.test(hook);
      staticFieldTest();
      B3 b3 = new B3();
      assert b3.i == 2;
      if (hook) {
        Albatross.hookObject(B3H.class, b3);
      }
      B3H b3h = Albatross.convert(b3, B3H.class);
      int b3h_backup_v = b3h.i;
      if (b3h_backup_v != 2) {
        Albatross.log("b3h_backup_v:" + b3h_backup_v);
        assert b3h_backup_v == 2;
      }
      staticFieldTest2(hook);
    }
    if (Albatross.getRuntimeISA() != Albatross.kArm)
      testMainTest();

    Albatross.backupAndHook(TestMain.class.getDeclaredMethod("testIntReturn"), TestMain.class.getDeclaredMethod("testReturnHook"), TestMain.class.getDeclaredMethod("testBackup"));
    Albatross.backupAndHook(PrintStream.class.getDeclaredMethod("println", String.class), TestMain.class.getDeclaredMethod("printlnHook", PrintStream.class, String.class), TestMain.class.getMethod("printlnBackup", PrintStream.class, String.class));
    Albatross.backup(A.class.getDeclaredMethod("a"), TestMain.class.getDeclaredMethod("aBackup", A.class));
    Albatross.backupAndHook(A2.class.getDeclaredMethod("a"), TestMain.class.getDeclaredMethod("a2Hook", A2.class), TestMain.class.getDeclaredMethod("a2Backup", A2.class));
    B b = new B();
    aBackup(b);
    b.a();
    A2 b2 = new B2();
    a2Backup(b2);
    if (hook) {
      int i = testIntReturn();
      assert i == 10;
      System.out.println("testIntReturn:" + i);
    }
    try {
      System.out.println("[x] testIntReturn2:" + testIntReturn());
      assert false;
    } catch (Exception e) {
      System.out.println("[y] testIntReturn2:");
    }
  }

  public static void testMainTest() throws NoSuchMethodException, AlbatrossException {
    TestMain testMain = new TestMain(2, 2);
    testMain.testCall(3);
    assert testMain.z == 5;
    assert testMain.testCall2(3) == 4;
//    assert TestMainH.testCall2(testMain, 3) == 3;
    assert Albatross.hookClass(TestMainH.class) == Albatross.CLASS_ALREADY_HOOK;
    Method testConstructorHook = TestMain.class.getDeclaredMethod("testConstructorHook");
    Albatross.log("compile testConstructorHook:" + Albatross.entryPointFromQuickCompiledCode(testConstructorHook)
        + " target:" + Albatross.entryPointFromQuickCompiledCode(TestMain.class.getDeclaredConstructor(int.class, int.class)));
//    Albatross.decompileMethod(TestMain.class.getDeclaredMethod("testConstructorHook"), true);
    testConstructorHook();
  }

  private static void staticFieldTest2(boolean hook) throws NoSuchMethodException {
    if (Albatross.containsFlags(Albatross.FLAG_FIELD_BACKUP_STATIC)) {
      Albatross.log("start staticFieldTest2");
      Method testAField = TestMain.class.getDeclaredMethod("testAField", int.class);
      if (hook) {
        if (Albatross.isCompiled(testAField)) {
          Albatross.log("testAField is compiled,decompiled it");
          Albatross.decompileMethod(testAField, false);
        }
        Albatross.compileMethod(testAField);
      }
      for (int i = 0; i < 300; i++) {
        testAField(i);
      }
      Albatross.log("end staticFieldTest2");
    }
  }

  private static void staticFieldTest() {
    if (Albatross.containsFlags(Albatross.FLAG_FIELD_BACKUP_STATIC)) {
      Albatross.log("static field test");
      int d = TestMainH.d;
      assert d == 7;
//        Albatross.backupFieldNative(TestMainH.class.getDeclaredField("i"), TestMain.class.getDeclaredField("i"));
      int iv = TestMain.i;
      for (int i = 0; i < 256; i++) {
        testAField(i);
      }
      Albatross.log("end static field test");
    }
  }

  private static void testConstructorHook() {
    TestMain testMain;
    testMain = new TestMain(-1, -1);
    assert testMain.z == 0;
    //will be compilation
    for (int i = 0; i < 512; i++) {
      int v = i + 3;
      testMain = new TestMain(v, v);
      assert testMain.z == v + 1;
    }
  }


  void testCall(int i) {
    z = i;
  }

  long testCall2(long z) {
    return z;
  }


  public int z;
  public static AtomicInteger initCount = new AtomicInteger();

  TestMain(int z, int r) {
    initCount.set(0);
    this.z = z;
  }

  static boolean isCalled = false;

  public static int testReturnHook() {
    if (Build.VERSION.SDK_INT < 35)
      Runtime.getRuntime().gc();
    if (isCalled)
      return testBackup() + 100;
    try {
      isCalled = true;
      return testBackup() + 1;
    } catch (Exception e) {
      System.out.println("cache Exception:" + e);
      return 10;
    }

  }

  public native static long aBackup(A a);

  private native static long a2Backup(A2 a2);

  public static long aHook(A a) {
    return aBackup(a);
  }

  public static long a2Hook(A2 a2) {
    return a2Backup(a2);
  }

  public native static int testBackup();

  public static void printlnHook(PrintStream z, String text) {
    printlnBackup(z, "[*] " + text);
  }

  public native static void printlnBackup(PrintStream z, String text);

  public static void testGc() {
    for (int i = 0; i < 1000; i++) {
      List<Integer> array = new ArrayList<>();
      for (int j = 0; j < 100; j++) {
        array.add(j);
      }
    }
    System.out.println("gc");
    Runtime.getRuntime().gc();
  }
}
