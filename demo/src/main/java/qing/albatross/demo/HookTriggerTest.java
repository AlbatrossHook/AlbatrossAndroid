package qing.albatross.demo;

import qing.albatross.annotation.MethodHook;
import qing.albatross.annotation.StaticMethodHook;
import qing.albatross.core.Albatross;
import qing.albatross.exception.AlbatrossErr;

public class HookTriggerTest {

  static class A {


    static int m1() {
      return 1;
    }

    String m2() {
      return "m2";
    }

  }

  static class AHook {

    static boolean hookM1;

    static boolean hookM2;

    @StaticMethodHook(triggerFieldName = "hookM1")
    static int m1() {
      return 2;
    }

    @MethodHook(triggerFieldName = "hookM2")
    private String m2() {
      return "hooked";
    }

  }

  public static void test() throws AlbatrossErr {
    AHook.hookM1 = false;
    AHook.hookM2 = false;
    int r = Albatross.hookClass(AHook.class, A.class);
    assert r == 0;
    assert A.m1() == 1;
    assert "m2".equals(new A().m2());
    r = Albatross.unhookClass(AHook.class, A.class);
    assert r == 0;
    AHook.hookM1 = true;
    AHook.hookM2 = true;
    r = Albatross.hookClass(AHook.class, A.class);
    assert r == 2;
    assert A.m1() == 2;
    assert "hooked".equals(new A().m2());
    r = Albatross.unhookClass(AHook.class, A.class);
    assert r == 2;
  }

}
