package qing.albatross.demo.android;

import android.app.Activity;
import android.os.Bundle;

import qing.albatross.annotation.FieldRef;
import qing.albatross.annotation.MethodBackup;
import qing.albatross.annotation.DefOption;
import qing.albatross.annotation.MethodHookBackup;
import qing.albatross.annotation.TargetClass;

@TargetClass(Activity.class)
public class ActivityH {

  //Active by ActivityH automatically
  @TargetClass(Bundle.class)
  public static class BundleH {
    @FieldRef
    public static Bundle EMPTY;

  }

  @FieldRef
  public boolean mCalled;


  @MethodBackup(option = DefOption.VIRTUAL)
  private native boolean isFinishing();

  public static boolean finish(ActivityH h) {
    return h.isFinishing();
  }

  @MethodHookBackup
  private void onCreate(BundleH savedInstanceState) {
    assert BundleH.EMPTY == Bundle.EMPTY;
    assert !mCalled;
    onCreate(savedInstanceState);
    assert mCalled;
  }
}
