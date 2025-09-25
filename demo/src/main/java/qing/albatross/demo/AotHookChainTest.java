package qing.albatross.demo;

import android.app.Activity;

import qing.albatross.annotation.ExecOption;
import qing.albatross.annotation.MethodHookBackup;
import qing.albatross.annotation.TargetClass;
import qing.albatross.core.Albatross;
import qing.albatross.exception.AlbatrossErr;

public class AotHookChainTest {

  static int callSeq = 0;

  @TargetClass(targetExec = ExecOption.AOT)
  private static class A0 {


    @MethodHookBackup
    private void onResume() {
      assert callSeq == 5;
      callSeq++;
      Albatross.log("A0.onResume start");
      onResume();
      Albatross.log("A0.onResume end");
      assert callSeq == 6;
    }

  }

  @TargetClass(targetExec = ExecOption.AOT)
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

  @TargetClass(targetExec = ExecOption.AOT)
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

  @TargetClass(targetExec = ExecOption.AOT)
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

  @TargetClass(targetExec = ExecOption.AOT)
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

  @TargetClass(targetExec = ExecOption.AOT)
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

  static class FakeActivity extends Activity {

    @Override
    public void onResume() {
      try {
        super.onResume();
      } catch (Exception ignore) {
      }
//      assert callSeq == 6;
      Albatross.log("finish Activity.onResume");
    }
  }


  static void test() {
    try {
      Albatross.hookClass(A0.class, Activity.class);
      Albatross.hookClass(A3.class, Activity.class);
      Albatross.hookClass(A1.class, Activity.class);
      Albatross.hookClass(A4.class, Activity.class);
      Albatross.hookClass(A2.class, Activity.class);
      Albatross.hookClass(A5.class, Activity.class);
    } catch (AlbatrossErr e) {
      throw new RuntimeException(e);
    }
    callSeq = 0;
    new FakeActivity().onResume();

  }

}
