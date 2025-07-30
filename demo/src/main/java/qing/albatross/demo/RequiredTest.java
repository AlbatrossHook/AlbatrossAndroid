package qing.albatross.demo;

import qing.albatross.annotation.FieldRef;
import qing.albatross.annotation.MethodBackup;
import qing.albatross.annotation.TargetClass;
import qing.albatross.core.Albatross;
import qing.albatross.exception.AlbatrossErr;
import qing.albatross.exception.HookInterfaceErr;
import qing.albatross.exception.RequiredClassErr;
import qing.albatross.exception.RequiredFieldErr;
import qing.albatross.exception.RequiredMethodErr;

public class RequiredTest {

  @TargetClass(className = "B")
  static class A {
    int i;

    int demo() {
      return 1;
    }
  }

  @TargetClass(A.class)
  static class AH {
    @FieldRef(required = true)
    public static int i;
  }

  @TargetClass(A.class)
  static class AH2 {
    @FieldRef(required = true)
    public static int b;
  }

  @TargetClass(A.class)
  static class AH3 {
    @MethodBackup(required = true)
    private native int demo();
  }

  @TargetClass(A.class)
  static class AH4 {
    @MethodBackup(required = true)
    private native int demo_();
  }

  @TargetClass(A.class)
  static class AH5 {
    @MethodBackup(required = true)
    private native long demo();
  }

  interface F {
    int ping();
  }

  static void test(boolean hook) {
    if (!hook)
      return;

    try {
      Albatross.hookClass(RequiredTest.class, F.class);
      assert false;
    } catch (AlbatrossErr e) {
      assert e instanceof HookInterfaceErr;
      assert ((HookInterfaceErr) e).targetClass == F.class;
    }
    try {
      Albatross.hookClass(A.class);
      throw new RuntimeException("should not reach there");
    } catch (AlbatrossErr e) {
      assert e instanceof RequiredClassErr;
    }
    if (Albatross.isFieldEnable()) {
      try {
        Albatross.hookClass(AH.class);
        throw new RuntimeException("should not reach there");
      } catch (AlbatrossErr e) {
        assert e instanceof RequiredFieldErr;
      }
      try {
        Albatross.hookClass(AH2.class);
        throw new RuntimeException("should not reach there");
      } catch (AlbatrossErr e) {
        assert e instanceof RequiredFieldErr;
      }
    }

    try {
      Albatross.hookClass(AH3.class);
    } catch (AlbatrossErr e) {
      throw new RuntimeException("should not reach there");
    }
    try {
      Albatross.hookClass(AH4.class);
      throw new RuntimeException("should not reach there");
    } catch (AlbatrossErr e) {
      assert e instanceof RequiredMethodErr;
    }
    try {
      Albatross.hookClass(AH5.class);
      throw new RuntimeException("should not reach there");
    } catch (AlbatrossErr e) {
      assert e instanceof RequiredMethodErr;
    }
  }

}
