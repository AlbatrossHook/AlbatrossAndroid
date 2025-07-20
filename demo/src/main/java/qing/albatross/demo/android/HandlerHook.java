package qing.albatross.demo.android;

import android.os.Handler;
import android.os.Message;

import qing.albatross.annotation.CompileOption;
import qing.albatross.annotation.MethodBackup;
import qing.albatross.annotation.MethodHook;
import qing.albatross.annotation.TargetClass;
import qing.albatross.core.Albatross;

@TargetClass(value = Handler.class, compileHooker = CompileOption.COMPILE_OPTIMIZED)
public class HandlerHook {
  static boolean catchException = true;

  @MethodBackup
  public static native void dispatchMessage(Handler handler, Message message);

  @MethodHook
  public static void dispatchMessage$Hook(Handler handler, Message message) {
    try {
      dispatchMessage(handler, message);
    } catch (Exception e) {
      if (!catchException)
        throw e;
      if (e.getCause() != null)
        Albatross.log("dispatchMessage",e.getCause());
      else
        Albatross.log("dispatchMessage",e);
    }
  }
}
