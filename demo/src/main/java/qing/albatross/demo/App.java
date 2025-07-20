package qing.albatross.demo;

import android.annotation.SuppressLint;
import android.app.Application;

import qing.albatross.core.Albatross;
import qing.albatross.demo.android.HandlerHook;

public class App extends Application {

  static boolean isTest;

  static App instance;

  public static boolean test() {
    try {
      if (isTest) {
        DemoMain.testEntry();
        return false;
      }
      isTest = true;
      assert Albatross.transactionLevel() == 0;
      Albatross.hookClass(HandlerHook.class);
      DemoMain.testEntry();
      return true;
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }


  @SuppressLint({"SoonBlockedPrivateApi", "BlockedPrivateApi"})
  @Override
  public void onCreate() {
    instance = this;
//    try {
//      View.class.getDeclaredMethod("getAccessibilityViewId");
//      Class <?> c = Class.forName("android.view.View$ListenerInfo");
//      c.getDeclaredField("mOnScrollChangeListener");
//    } catch (Exception e) {
//      throw new RuntimeException(e);
//    }
    super.onCreate();
  }
}
