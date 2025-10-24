package qing.albatross.demo;

import qing.albatross.annotation.MethodBackup;
import qing.albatross.annotation.MethodHook;
import qing.albatross.annotation.MethodHookBackup;
import qing.albatross.core.Albatross;
import qing.albatross.exception.AlbatrossErr;
import qing.albatross.reflection.VoidMethodDef;

public class UnHookTest2 {

  static int callSeq = 0;

  private static class A0 {


    @MethodBackup
    private native void onResume$Backup();


    @MethodHook
    private void onResume() {
      callSeq |= 1;
      Albatross.log("A0.onResume start");
      onResume$Backup();
      Albatross.log("A0.onResume end");
    }

    static VoidMethodDef onResume;

  }

  private static class A1 {


    @MethodBackup
    private native void onResume$Backup();


    @MethodHook
    private void onResume() {
      callSeq |= 16;
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
      callSeq |= 128;
      onResume$Backup();
      Albatross.log("A2.onResume end");
    }

  }

  private static class A3 {
    @MethodHookBackup
    private void onResume() {
      callSeq |= 4;
      Albatross.log("A3.onResume start");
      onResume();
      Albatross.log("A3.onResume end");
    }

  }

  private static class A4 {


    @MethodHookBackup
    private void onResume() {
      callSeq |= 32;
      Albatross.log("A4.onResume start");
      onResume();
      Albatross.log("A4.onResume end");
    }

  }

  private static class A5 {


    @MethodHookBackup
    private void onResume() {
      callSeq |= 64;
      Albatross.log("A5.onResume start");
      onResume();
      Albatross.log("A5.onResume end");
    }

  }

  static class FakeActivity {
    void onResume() {
      callSeq |= 8;
      Albatross.log("Activity.onResume");
    }
  }


  static void test() throws AlbatrossErr, NoSuchMethodException {
    callSeq = 0;
    if (A0.onResume != null)
      return;
    int count;
    Albatross.hookClass(A0.class, FakeActivity.class);//1
    Albatross.hookClass(A1.class, FakeActivity.class);//16
    Albatross.hookClass(A4.class, FakeActivity.class);//32
    Albatross.hookClass(A2.class, FakeActivity.class);//128
    Albatross.hookClass(A5.class, FakeActivity.class);//64
    count = Albatross.getMethodHookCount(A0.onResume.method);
    assert count == 5;
    callSeq = 0;
    new FakeActivity().onResume();
    assert callSeq == (16 | 32 | 128 | 64 | 8 | 1);

    assert Albatross.unhookMethod(FakeActivity.class.getDeclaredMethod("onResume"), A0.class.getDeclaredMethod("onResume"), A0.class.getDeclaredMethod("onResume$Backup"));
    callSeq = 0;
    new FakeActivity().onResume();
    assert callSeq == (16 | 128 | 32 | 64 | 8);
    count = Albatross.getMethodHookCount(A0.onResume.method);
    assert count == 4;

    assert Albatross.unhookMethod(FakeActivity.class.getDeclaredMethod("onResume"), A5.class.getDeclaredMethod("onResume"), A5.class.getDeclaredMethod("onResume"));
    callSeq = 0;
    new FakeActivity().onResume();
    assert callSeq == (16 | 32 | 8 | 128);
    count = Albatross.getMethodHookCount(A0.onResume.method);
    assert count == 3;


    assert Albatross.unhookMethod(FakeActivity.class.getDeclaredMethod("onResume"), A2.class.getDeclaredMethod("onResume"), A2.class.getDeclaredMethod("onResume$Backup"));
    callSeq = 0;
    new FakeActivity().onResume();
    assert callSeq == (16 | 32 | 8);
    count = Albatross.getMethodHookCount(A0.onResume.method);
    assert count == 2;


    assert Albatross.unhookMethod(FakeActivity.class.getDeclaredMethod("onResume"), A1.class.getDeclaredMethod("onResume"), A1.class.getDeclaredMethod("onResume$Backup"));
    count = Albatross.getMethodHookCount(A0.onResume.method);
    assert count == 1;
    callSeq = 0;
    new FakeActivity().onResume();
    assert callSeq == (32 | 8);

    assert Albatross.unhookMethod(FakeActivity.class.getDeclaredMethod("onResume"), A4.class.getDeclaredMethod("onResume"), A4.class.getDeclaredMethod("onResume"));
    count = Albatross.getMethodHookCount(A0.onResume.method);
    assert count == 0;
    callSeq = 0;
    new FakeActivity().onResume();
    assert callSeq == 8;
  }

}
