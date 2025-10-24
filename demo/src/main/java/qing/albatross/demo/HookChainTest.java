package qing.albatross.demo;

import java.lang.reflect.Method;

import qing.albatross.annotation.MethodBackup;
import qing.albatross.annotation.MethodHook;
import qing.albatross.annotation.MethodHookBackup;
import qing.albatross.core.Albatross;
import qing.albatross.exception.AlbatrossErr;
import qing.albatross.reflection.VoidMethodDef;

public class HookChainTest {

  static int callSeq = 0;

  private static class A0 {


    @MethodBackup
    private native void onResume$Backup();


    @MethodHook
    private void onResume() {
      assert callSeq == 5;
      callSeq++;
      Albatross.log("A0.onResume start");
      onResume$Backup();
      Albatross.log("A0.onResume end");
      assert callSeq == 7;
    }

    static VoidMethodDef onResume;

  }

  private static class A1 {


    @MethodBackup
    private native void onResume$Backup();


    @MethodHook
    private void onResume() {
      assert callSeq == 3;
      callSeq++;
      Albatross.log("A1.onResume start");
      onResume$Backup();
      Albatross.log("A1.onResume end");
    }

  }

  private static class A2 {


    @MethodBackup
    private native void onResume$Backup();


    @MethodHook
    private void onResume() {
      Albatross.log("A2.onResume start");
      assert callSeq == 1;
      callSeq++;
      onResume$Backup();
      Albatross.log("A2.onResume end");
    }

  }

  private static class A3 {
    @MethodHookBackup
    private void onResume() {
      assert callSeq == 4;
      callSeq++;
      Albatross.log("A3.onResume start");
      onResume();
      Albatross.log("A3.onResume end");
    }

  }

  private static class A4 {


    @MethodHookBackup
    private void onResume() {
      assert callSeq == 2;
      callSeq++;
      Albatross.log("A4.onResume start");
      onResume();
      Albatross.log("A4.onResume end");
    }

  }

  private static class A5 {


    @MethodHookBackup
    private void onResume() {
      assert callSeq == 0;
      callSeq++;
      Albatross.log("A5.onResume start");
      onResume();
      Albatross.log("A5.onResume end");
    }

  }

  static class FakeActivity {
    void onResume() {
      if (callSeq != 0) {
        assert callSeq == 6;
        callSeq++;
      } else {
        callSeq = -1;
      }

      Albatross.log("Activity.onResume");
    }
  }


  static void test() throws AlbatrossErr {

    callSeq = 0;
    new FakeActivity().onResume();
    assert callSeq == -1;

    int a0 = Albatross.hookClass(A0.class, FakeActivity.class);
    assert a0 > 0;
    int a3 = Albatross.hookClass(A3.class, FakeActivity.class);
    assert a3 > 0;
    int a1 = Albatross.hookClass(A1.class, FakeActivity.class);
    assert a1 > 0;
    int a4 = Albatross.hookClass(A4.class, FakeActivity.class);
    assert a4 > 0;
    int a2 = Albatross.hookClass(A2.class, FakeActivity.class);
    assert a2 > 0;
    int a5 = Albatross.hookClass(A5.class, FakeActivity.class);
    assert a5 > 0;
    Method method = A0.onResume.method;
    int count = Albatross.getMethodHookCount(method);
    assert count == 6;
    callSeq = 0;
    new FakeActivity().onResume();
    assert callSeq == 7;

    int a00 = Albatross.unhookClass(A0.class, FakeActivity.class);
    assert a00 == a0;
    a00 = Albatross.unhookClass(A0.class, FakeActivity.class);
    assert a00 == -1;
    count = Albatross.getMethodHookCount(method);
    assert count == 5;
    assert A0.onResume == null;

    int a55 = Albatross.unhookClass(A5.class, FakeActivity.class);
    assert a55 == a5;
    a00 = Albatross.unhookClass(A5.class, FakeActivity.class);
    assert a00 == -1;
    count = Albatross.getMethodHookCount(method);
    assert count == 4;

    int a33 = Albatross.unhookClass(A3.class, FakeActivity.class);
    assert a33 == a3;
    a00 = Albatross.unhookClass(A3.class, FakeActivity.class);
    assert a00 == -1;
    count = Albatross.getMethodHookCount(method);
    assert count == 3;


    int a11 = Albatross.unhookClass(A1.class, FakeActivity.class);
    assert a11 == a1;
    a00 = Albatross.unhookClass(A1.class, FakeActivity.class);
    assert a00 == -1;
    count = Albatross.getMethodHookCount(method);
    assert count == 2;

    int a44 = Albatross.unhookClass(A4.class, FakeActivity.class);
    assert a44 == a4;
    a00 = Albatross.unhookClass(A1.class, FakeActivity.class);
    assert a00 == -1;
    count = Albatross.getMethodHookCount(method);
    assert count == 1;

    int a22 = Albatross.unhookClass(A2.class, FakeActivity.class);
    assert a22 == a2;
    a00 = Albatross.unhookClass(A2.class, FakeActivity.class);
    assert a00 == -1;
    count = Albatross.getMethodHookCount(method);
    assert count == 0;

    callSeq = 0;
    new FakeActivity().onResume();
    assert callSeq == -1;
  }

}
