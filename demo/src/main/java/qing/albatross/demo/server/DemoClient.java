package qing.albatross.demo.server;

import qing.albatross.core.Albatross;
import qing.albatross.server.UnixRpcClientInstance;

public class DemoClient extends UnixRpcClientInstance implements DemoApi {
  @Override
  public native byte callApi(String arg);

  @Override
  public native String callReturnObject(int i);

  @Override
  public String broadcast(String data) {
    return data + " from client";
  }

  @Override
  public short jsonObject(String dict, String list) {
    return 0;
  }

  @Override
  public byte broadcastLongArgTest(double d1, String s2, byte b3, long l4, double d5, byte b6, String s7, int i8, double i9, byte b10, float s11) {
    Albatross.log("broadcastLongArgTest:" + s2);
    return b3;
  }

  @Override
  public native byte receiveLongArgTest(double d1, String s2, byte b3, long l4, double d5, byte b6, String s7, int i8, double i9, byte b10, float s11);

  @Override
  public int sendPrimary(String s, int i) {
    return s.length() + i;
  }

  @Override
  public byte sendLongString(String s) {
    Albatross.log("sendLongString:" + s);
    return 123;
  }

  @Override
  public void sendNoReturn(long i) {
    Albatross.log("sendNoReturn:" + i);
  }

  @Override
  public native void throwException(String s);

  @Override
  public native String startActivity(String pkgName, String activity, int uid);

  @Override
  protected Class<?> getApi() {
    return DemoApi.class;
  }
}
