package qing.albatross.demo;

import qing.albatross.annotation.ExecOption;
import qing.albatross.annotation.DefOption;
import qing.albatross.annotation.FieldRef;
import qing.albatross.annotation.TargetClass;
import qing.albatross.core.Albatross;
import qing.albatross.exception.AlbatrossErr;

public class FieldRefTest {


  static class B {
    static long l = 666;
    Integer i = 7777;
  }

  static class A extends B {
    int a;
    static final String b = "123";
  }

  @TargetClass(A.class)
  static class AH {

    @FieldRef("a")
    int _a;
    @FieldRef("b")
    static String _b = "567";


    @FieldRef(value = {"ll", "l"}, option = DefOption.VIRTUAL)
    static long _l = 1666;
    @FieldRef(value = {"ii", "i"}, option = DefOption.VIRTUAL)
    Integer _i = 17777;
  }

  static void test(boolean hook) throws AlbatrossErr {
    if (!Albatross.isFieldEnable())
      return;
    if (hook) {
      assert Albatross.hookClass(AH.class, A.class) == 4;
      Albatross.compileClass(FieldRefTest.class, ExecOption.RECOMPILE_OPTIMIZED);
    } else {
      B.l = 666L;
      assert AH._b.equals(A.b);
      assert AH._b == A.b;
      A a = new A();
      a.a = 7458;
      AH ah = Albatross.convert(a, AH.class);
      assert ah._a == 7458;
      assert ah._i == 7777;
      assert AH._l == 666L;
      AH._l = 8888;
      //Prevent compiler optimization，aa is a
      A aa = Albatross.convert(a, A.class);
      assert B.l == 8888;
      ah._i = 555;
      assert aa.i == 555;
      aa.a = 321;
      assert ah._a == 321;
    }
  }

}
