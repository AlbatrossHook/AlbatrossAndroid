package qing.albatross.demo;

import android.net.wifi.WifiInfo;

import qing.albatross.annotation.ArgumentType;
import qing.albatross.annotation.TargetClass;
import qing.albatross.core.Albatross;
import qing.albatross.exception.AlbatrossErr;
import qing.albatross.reflection.VoidMethodDef;

@TargetClass(WifiInfo.class)
class WifiInfoH {
  @ArgumentType(String.class)
  public static VoidMethodDef setBSSID;
  @ArgumentType(value = Object.class, exactSearch = false)
  public static VoidMethodDef setSSID;
  @ArgumentType(String.class)
  public static VoidMethodDef setMacAddress;

}

public class MethodDefTest {


  static void test(boolean hook) throws AlbatrossErr {
    if (hook) {
      int r = Albatross.hookClass(WifiInfoH.class, WifiInfo.class);
      assert r >= 3;
      assert WifiInfoH.setSSID != null;
    }
  }
}
