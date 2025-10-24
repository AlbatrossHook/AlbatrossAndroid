package qing.albatross.demo;

import qing.albatross.annotation.MethodBackup;
import qing.albatross.annotation.MethodHook;
import qing.albatross.annotation.MethodHookBackup;
import qing.albatross.core.Albatross;
import qing.albatross.exception.AlbatrossErr;
import qing.albatross.reflection.VoidMethodDef;

public class HookChainTest2 {

  static int callSeq = 0;

  private static class A0 {

    @MethodHookBackup
    private void onResume() {
      assert callSeq == 5;
      callSeq++;
      Albatross.log("A0.onResume start");
      onResume();
      Albatross.log("A0.onResume end");
      assert callSeq == 7;
    }

    static VoidMethodDef onResume;

  }

  private static class A1 {

    @MethodHookBackup
    private void onResume() {
      assert callSeq == 3;
      callSeq++;
      Albatross.log("A1.onResume start");
      onResume();
      Albatross.log("A1.onResume end");
    }

  }

  private static class A2 {


    @MethodHookBackup
    private void onResume() {
      Albatross.log("A2.onResume start");
      assert callSeq == 1;
      callSeq++;
      onResume();
      Albatross.log("A2.onResume end");
    }

  }

  private static class A3 {
    @MethodBackup
    private native void onResume$Backup();

    @MethodHook
    private void onResume() {
      assert callSeq == 4;
      callSeq++;
      Albatross.log("A3.onResume start");
      onResume$Backup();
      Albatross.log("A3.onResume end");
    }

  }

  private static class A4 {

    @MethodBackup
    private native void onResume$Backup();

    @MethodHook
    private void onResume() {
      assert callSeq == 2;
      callSeq++;
      Albatross.log("A4.onResume start");
      onResume$Backup();
      Albatross.log("A4.onResume end");
    }

  }

  private static class A5 {

    @MethodBackup
    private native void onResume$Backup();

    @MethodHook
    private void onResume() {
      assert callSeq == 0;
      callSeq++;
      Albatross.log("A5.onResume start");
      onResume$Backup();
      Albatross.log("A5.onResume end");
    }
  }

  static class FakeActivity {
    void onResume() {
      assert callSeq == 6;
      callSeq++;
      Albatross.log("Activity.onResume");
    }
  }


  static void test() {
    Albatross.log("HookChainTest2 start");
    try {
      Albatross.hookClass(A0.class, FakeActivity.class);
      Albatross.hookClass(A3.class, FakeActivity.class);
      Albatross.hookClass(A1.class, FakeActivity.class);
      Albatross.hookClass(A4.class, FakeActivity.class);
      Albatross.hookClass(A2.class, FakeActivity.class);
      Albatross.hookClass(A5.class, FakeActivity.class);
      int count = Albatross.getMethodHookCount(A0.onResume.method);
      assert count == 6;
    } catch (AlbatrossErr e) {
      throw new RuntimeException(e);
    }
    callSeq = 0;
    new FakeActivity().onResume();
    Albatross.log("HookChainTest2 finish");
  }

}
