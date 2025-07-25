package qing.albatross.demo.android;

import android.os.Handler;
import android.os.Message;

import qing.albatross.annotation.ExecOption;
import qing.albatross.annotation.MethodBackup;
import qing.albatross.annotation.MethodHook;
import qing.albatross.annotation.TargetClass;
import qing.albatross.core.Albatross;
import qing.albatross.exception.AlbatrossException;

@TargetClass(value = Handler.class, hookerExec = ExecOption.JIT_OPTIMIZED)
public class HandlerHook {

  @MethodBackup
  public static native void dispatchMessage(Handler handler, Message message);

  @MethodHook
  public static void dispatchMessage$Hook(Handler handler, Message message) throws Throwable {
    try {
      dispatchMessage(handler, message);
    } catch (Throwable e) {
      Throwable reason = e;
      if (e.getCause() != null)
        reason = e.getCause();
      if (reason instanceof AssertionError)
        throw reason;
      if (reason instanceof AlbatrossException)
        throw reason;
      Albatross.log("dispatchMessage", reason);
    }
  }
}
