package qing.albatross.demo;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.Window;

import java.util.Map;

import qing.albatross.annotation.ArgumentTypeSlot;
import qing.albatross.annotation.MethodBackup;
import qing.albatross.annotation.TargetClass;
import qing.albatross.core.Albatross;
import qing.albatross.reflection.MethodDef;

@TargetClass(Activity.class)
public class ActivityHook {
  //  boolean mResumed;
//  @MethodBackup
//  @MethodHook
//  private void onCreate(Bundle savedInstanceState) {
//    assert mResumed = false;
//    onCreate(savedInstanceState);
//    assert mResumed == true;
//  }
  public static Map<Object, String> exceptions;

  @MethodBackup
  private native void onCreate(Bundle savedInstanceState);

  @ArgumentTypeSlot
  public static MethodDef<Void> onCreate;

  @ArgumentTypeSlot("getIntent")
  public static MethodDef<Object> getIntentMethod;

  public static MethodDef<Window> getWindow;

  public static MethodDef<Object> onSaveInstanceState;

  @MethodBackup
  private native Intent getIntent();


  @MethodBackup
  private native boolean onResume();

  @ArgumentTypeSlot
  public static MethodDef<Void> onResume;

  public static void test(boolean hook) {
    if (hook)
      assert Albatross.hookClass() >= 5;
    else
      assert Albatross.hookClass() == Albatross.CLASS_ALREADY_HOOK;
    assert ActivityHook.onCreate != null;
    assert ActivityHook.getIntentMethod != null;
    assert ActivityHook.getWindow != null;
    assert ActivityHook.onSaveInstanceState == null;
    assert ActivityHook.onResume == null;
  }

}
