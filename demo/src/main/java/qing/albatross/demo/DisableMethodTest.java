package qing.albatross.demo;

import qing.albatross.core.Albatross;

public class DisableMethodTest {

  public static int a(int i) {
    return i + 3;
  }

  public String b(int b) {
    return "s" + b;
  }

  public static int a() {
    return 10000;
  }

  public String bb(int b) {
    return "s" + b;
  }


  static void test(boolean hook) throws NoSuchMethodException {
    assert hook == Albatross.disableMethod(DisableMethodTest.class.getDeclaredMethod("a", int.class));
    assert hook == Albatross.disableMethod(DisableMethodTest.class.getDeclaredMethod("b", int.class));
    assert hook == Albatross.disableMethod(DisableMethodTest.class.getDeclaredMethod("a"), true);
    assert hook == Albatross.disableMethod(DisableMethodTest.class.getDeclaredMethod("bb", int.class), true);
    assert a(123) == 0;
    assert new DisableMethodTest().b(1234) == null;
    try {
      int r = DisableMethodTest.a();
      throw new RuntimeException("exception should be thrown:" + r);
    } catch (Exception ignore) {
    }
    try {
      new DisableMethodTest().bb(123);
      throw new RuntimeException("exception should be thrown");
    } catch (Exception ignore) {
    }
  }


}
