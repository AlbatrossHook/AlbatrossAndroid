package qing.albatross.demo;

import static qing.albatross.demo.TestMain.testGc;

import qing.albatross.nativehook.AlbNative;
import qing.albatross.nativehook.HookRecord;

public class NativeInstructionHookTest {

  static native int getInt(int i1, byte b2, long l3);

  static native int getIntNativeThread(int i1, byte b2, long l3);


  static int count = 0;

  public static void test() {
    int v = getInt(1, (byte) 2, 3);
    assert v == 12 : v;
    v = getIntNativeThread(2, (byte) 3, 3);
    assert v == 23 : v;
    HookRecord hookRecord = AlbNative.hookInstruction("api", "Java_qing_albatross_demo_NativeInstructionHookTest_getInt", (invokeContext, userdata) -> {
      boolean javaThread = invokeContext.isJavaThread();
      long a2 = invokeContext.getNthArgument(2);
      long a3 = invokeContext.getNthArgument(3);
      if ((count & 64) == 0) {
        testGc();
      }
      count++;
      if (javaThread) {
        assert a2 == 1 : a2;
        assert a3 == 2 : a3;
        Class<?> clz = invokeContext.getNthArgument(1, Class.class);
        assert clz == NativeInstructionHookTest.class;
        invokeContext.setNthArgument(2, 4);
        invokeContext.setNthArgument(3, 5);
      } else {
        assert a2 == 6 : a2;
        assert a3 == 7 : a3;
        Class<?> clz = invokeContext.getNthArgument(1, Class.class);
        assert clz == null;
        invokeContext.setNthArgument(2, 8);
        invokeContext.setNthArgument(3, 9);
      }
    }, ((invokeContext, userdata) -> {
      boolean javaThread = invokeContext.isJavaThread();
      long result = invokeContext.getResult();
      if (javaThread) {
        assert result == 45 : result;
        invokeContext.setResult(1222);
      } else {
        assert result == 89 : result;
        invokeContext.setResult(2048);
      }
    }), 1);
    callHookMethod(hookRecord, 1);

    Thread thread = new Thread() {
      @Override
      public void run() {
        callHookMethod(hookRecord, 512);
      }
    };
    thread.start();
    callHookMethod(hookRecord, 256);
    try {
      thread.join();
    } catch (InterruptedException e) {
      throw new RuntimeException(e);
    }
    assert hookRecord.unHook();
    v = getInt(1, (byte) 2, 3);
    assert v == 12 : v;
    assert !hookRecord.unHook();
    v = getInt(2, (byte) 3, 3);
    assert v == 23 : v;
  }

  private static void callHookMethod(HookRecord hookRecord, int tryTime) {
    int v;
    for (int i = 0; i < tryTime; i++) {
      assert hookRecord != null;
      v = getInt(1, (byte) 2, 3);
      assert v == 1222 : v;
      v = getIntNativeThread(6, (byte) 7, 3);
      assert v == 2048 : v;
    }
  }
}
