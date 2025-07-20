package qing.albatross.demo.server;

import qing.albatross.server.Broadcast;

public interface DemoApi {

  byte callApi(String arg);

  String callReturnObject(int i);

  @Broadcast
  String broadcast(String data);

  @Broadcast
  short jsonObject(String dict, String list);

  @Broadcast
  byte broadcastLongArgTest(double d1, String s2, byte b3, long l4, double d5, byte b6, String s7, int i8, double i9, byte b10, float s11);

  @Broadcast
  int sendPrimary(String s, int i);

  @Broadcast
  byte sendLongString(String s);

  @Broadcast
  void sendNoReturn(long i);

  void throwException(String s);

  String startActivity(String pkgName, String activity, int uid);

}
