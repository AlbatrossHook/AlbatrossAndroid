package qing.albatross.demo.server;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.UserHandle;

import qing.albatross.core.Albatross;
import qing.albatross.server.UnixRpcInstance;

public class DemoServer extends UnixRpcInstance implements DemoApi {
  public static final String STRING_SUCCESS = "success";

  @Override
  public byte callApi(String arg) {
    return 12;
  }

  @Override
  public String callReturnObject(int i) {
    return "result" + i;
  }

  @Override
  public native String broadcast(String data);

  @Override
  public native short jsonObject(String dict, String list);


  @Override
  public native byte broadcastLongArgTest(double d1, String s2, byte b3, long l4, double d5, byte b6, String s7, int i8, double d9, byte b10, float f11);

  @Override
  public native int sendPrimary(String s, int i);

  @Override
  public native byte sendLongString(String s);

  @Override
  public native void sendNoReturn(long i);

  @Override
  public void throwException(String s) {
    throw new RuntimeException("test exception " + s);
  }

  @Override
  protected Class<?> getApi() {
    return DemoApi.class;
  }

  @Override
  public String startActivity(String pkgName, String activity, int uid) {
    UserHandle user;
    Context ctx;
    PackageManager pm;
    Context context = Albatross.currentApplication();
    pm = context.getPackageManager();
    ctx = context;
    Intent intent = pm.getLaunchIntentForPackage(pkgName);
    if (intent == null) {
      intent = pm.getLeanbackLaunchIntentForPackage(pkgName);
    }
    if (intent == null) {
      if (activity == null)
        return "Unable to find a front-door activity for " + pkgName;
      intent = new Intent();
      intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    }
    if (activity != null) {
      intent.setClassName(pkgName, activity);
    }
    ctx.startActivity(intent);
    return STRING_SUCCESS;
  }

}
