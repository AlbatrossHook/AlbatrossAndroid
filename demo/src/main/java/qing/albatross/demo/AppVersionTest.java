package qing.albatross.demo;

import qing.albatross.annotation.MethodHook;
import qing.albatross.annotation.StaticMethodHook;
import qing.albatross.common.AppMetaInfo;
import qing.albatross.core.Albatross;
import qing.albatross.exception.AlbatrossErr;

public class AppVersionTest {

  static class A {


    static int m1() {
      return 1;
    }

    String m2() {
      return "m2";
    }

  }

  static class AHook {


    @StaticMethodHook(appVersion = -1)
    static int m1() {
      return 2;
    }

    @MethodHook(maxAppVersion = -2)
    private String m2() {
      return "hooked";
    }

  }

  static class AHook2 {


    @StaticMethodHook(appVersion = 100)
    static int m1() {
      return 3;
    }

    @MethodHook(minAppVersion = 99, maxAppVersion = 101)
    private String m2() {
      return "hooked3";
    }

  }

  public static void test() throws AlbatrossErr {
    int r = Albatross.hookClass(AHook.class, A.class);
    assert r == 0;
    assert A.m1() == 1;
    assert "m2".equals(new A().m2());
    r = Albatross.unhookClass(AHook.class, A.class);
    assert r == 0;
    if (AppMetaInfo.versionCode == 100) {
      r = Albatross.hookClass(AHook2.class, A.class);
      assert r == 2;
      assert A.m1() == 3;
      assert "hooked3".equals(new A().m2());
      r = Albatross.unhookClass(AHook2.class, A.class);
      assert r == 2;
    }

  }

}
