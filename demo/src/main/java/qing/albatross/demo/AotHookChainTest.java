package qing.albatross.demo;

import android.app.Activity;

import java.lang.reflect.Method;

import qing.albatross.annotation.ExecOption;
import qing.albatross.annotation.FieldRef;
import qing.albatross.annotation.MethodHookBackup;
import qing.albatross.annotation.TargetClass;
import qing.albatross.core.Albatross;
import qing.albatross.exception.AlbatrossErr;
import qing.albatross.reflection.VoidMethodDef;

public class AotHookChainTest {

  static int callSeq = 0;

  @TargetClass(targetExec = ExecOption.AOT)
  private static class A0 {

    @FieldRef
    boolean mResumed;

    @FieldRef
    static int RESULT_OK;

    @MethodHookBackup
    private void onResume() {
      assert callSeq == 5;
      callSeq++;
      Albatross.log("A0.onResume start");
      onResume();
      Albatross.log("A0.onResume end");
      assert callSeq == 6;
    }

    static VoidMethodDef onResume;

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
      Albatross.log("finish Activity.onResume");
    }
  }


  static void test() throws AlbatrossErr {
    int a0 = Albatross.hookClass(A0.class, Activity.class);
    int a3 = Albatross.hookClass(A3.class, Activity.class);
    int a1 = Albatross.hookClass(A1.class, Activity.class);
    int a4 = Albatross.hookClass(A4.class, Activity.class);
    int a2 = Albatross.hookClass(A2.class, Activity.class);
    int a5 = Albatross.hookClass(A5.class, Activity.class);
    callSeq = 0;
    Method method = A0.onResume.method;
    if (Albatross.isFieldEnable())
      assert A0.RESULT_OK == Activity.RESULT_OK;
    int hookCount = Albatross.getMethodHookCount(method);
    assert hookCount >= 6;
    new FakeActivity().onResume();
    assert callSeq == 6;
    int a00 = Albatross.unhookClass(A0.class, Activity.class);
    assert a00 <= a0;
    int a33 = Albatross.unhookClass(A3.class, Activity.class);
    assert a33 == a3;
    int a11 = Albatross.unhookClass(A1.class, Activity.class);
    assert a11 == a1;
    int a44 = Albatross.unhookClass(A4.class, Activity.class);
    assert a44 == a4;
    int a22 = Albatross.unhookClass(A2.class, Activity.class);
    assert a22 == a2;
    int a55 = Albatross.unhookClass(A5.class, Activity.class);
    assert a55 == a5;
    assert Albatross.getMethodHookCount(method) + 6 == hookCount;
    callSeq = 0;
    new FakeActivity().onResume();
    assert callSeq == 0;
  }

}
