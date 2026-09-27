package qing.albatross.demo;

import qing.albatross.annotation.FieldRef;
import qing.albatross.annotation.MethodHookBackup;
import qing.albatross.annotation.TargetClass;
import qing.albatross.core.Albatross;
import qing.albatross.exception.AlbatrossErr;

public class HookWayTest {


  static class A {
    int a;

    int invoke(int arg) {
      return arg + 1;
    }
  }

  @TargetClass
  static class AH {
    @FieldRef
    int a;
  }

  @MethodHookBackup(className = "qing.albatross.demo.HookWayTest$A")
  static int invoke(AH ah, int i) {
    return ah.a + invoke(ah, i);
  }

  public static void test() throws AlbatrossErr {
    Albatross.hookClass(HookWayTest.class);
    A a = new A();
    a.a = 100;
    int v = a.invoke(4);
    assert v == 105 : v;
  }
}
