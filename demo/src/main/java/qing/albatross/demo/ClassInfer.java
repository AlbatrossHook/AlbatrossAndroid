package qing.albatross.demo;

import android.app.Activity;
import android.app.Application;
import android.content.Intent;
import android.os.IBinder;

import java.util.Map;

import qing.albatross.annotation.FieldRef;
import qing.albatross.annotation.MethodBackup;
import qing.albatross.annotation.DefOption;
import qing.albatross.annotation.StaticMethodBackup;
import qing.albatross.annotation.StaticMethodHookBackup;
import qing.albatross.annotation.TargetClass;
import qing.albatross.core.Albatross;
import qing.albatross.exception.AlbatrossErr;

public class ClassInfer {

  static class A {
    int i;

    static A create(int i) {
      A a = new A();
      a.i = i;
      return a;
    }
  }

  @TargetClass
  static class AH {
    @FieldRef
    int i;

  }

  @TargetClass(A.class)
  static class C {

    static int count = 0;

    @StaticMethodHookBackup
    static AH create(int i) throws Exception {
      if (count++ > 15)
        throw new Exception("C create recursive call");
      AH a = create(i + 1);
      a.i = i * 100 + a.i;
      return a;
    }
  }


  @TargetClass
  public static class ActivityClientRecord {
    @FieldRef
    public LoadedApk packageInfo;
    @FieldRef
    public Intent intent;
  }

  @TargetClass
  public static class LoadedApk {
    @FieldRef
    public String mPackageName;
  }

  @TargetClass(className = "android.app.ActivityThread")
  public static class ActivityThreadH {
    public static Class<?> Class;

    @StaticMethodBackup
    public static native Application currentApplication();

    //该方法仅仅是为了推导出ActivityClientRecord的正确类型，不会backup Method,所以标记MethodDefOption.NOTHING
    @MethodBackup(option = DefOption.NOTHING)
    private native Activity performLaunchActivity(ActivityClientRecord r, Intent customIntent);

    //泛型的具体类型无法动态获取，所以需要通过上面的方法去推断出类型和依赖
    @FieldRef
    Map<IBinder, ActivityClientRecord> mActivities;

    @StaticMethodBackup
    public static native ActivityThreadH currentActivityThread();
  }


  public static void test(boolean isHook) throws AlbatrossErr {
    assert Albatross.hookClass(ActivityThreadH.class) != 0;
    ActivityThreadH activityThread = ActivityThreadH.currentActivityThread();
    assert activityThread.getClass() == ActivityThreadH.Class;
    Application app = ActivityThreadH.currentApplication();
    String targetPackage = app.getPackageName();

    if (Albatross.isFieldEnable()) {
      for (ActivityClientRecord record : activityThread.mActivities.values()) {
        assert targetPackage.equals(record.packageInfo.mPackageName);
      }
    }
    Albatross.hookClass(C.class);
    C.count = 0;
    assert Albatross.isHooked(AH.class);
    for (int i = 0; i < 13; i++) {
      A a = A.create(i);
      int expect = i * 100 + 1 + i;
      if (a.i != expect) {
        throw new RuntimeException("a.i:" + a.i + " expect:" + expect);
      }
    }
  }
}
