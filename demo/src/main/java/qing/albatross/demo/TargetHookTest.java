package qing.albatross.demo;

import android.util.Log;

import qing.albatross.annotation.MethodBackup;
import qing.albatross.annotation.MethodHook;
import qing.albatross.annotation.ParamInfo;
import qing.albatross.annotation.StaticMethodHookBackup;
import qing.albatross.annotation.TargetClass;
import qing.albatross.core.Albatross;
import qing.albatross.exception.AlbatrossErr;
import qing.albatross.reflection.ConstructorDef;

public class TargetHookTest {

  static class A {
    int a() {
      return 1;
    }

    public final int b(int i) {
      return i + 1;
    }

  }

  @TargetClass(A.class)
  static class AH {
    @MethodBackup
    private native int a();

    public static ConstructorDef<AH> create;
  }

  static class C {
    static public int A1(A a) {
      return a.a();
    }

    static public A A2(int i) {
      return new A();
    }

    public int B1(A a) {
      return a.a();
    }

    public A B2(int i) {
      return new A();
    }

    public String string(String s) {
      return "string:" + s;
    }

  }

  @TargetClass(C.class)
  static class CH {

    static ConstructorDef<CH> create;

    @MethodBackup
    @MethodHook(isStatic = true)
    static public int A1(AH b) {
      return b.a() + 2;
    }

    @StaticMethodHookBackup
    static public AH A2(int i) {
      AH b = AH.create.newInstance();
      b.a();
      return b;
    }

    @MethodBackup
    native private int B1(A a);

    static int stringCallCount = 0;

    @MethodBackup
    @MethodHook
    private String string(@ParamInfo("java.lang.String") Object s) throws NoSuchMethodException {
      if (stringCallCount++ > 16) {
        long entry = Albatross.entryPointFromQuickCompiledCode(CH.class.getDeclaredMethod("string", Object.class));
        Albatross.log( "string can not call target method:" + entry);
      } else if (stringCallCount++ > 18) {
        long entry = Albatross.entryPointFromQuickCompiledCode(C.class.getDeclaredMethod("string", String.class));
        throw new RuntimeException("string can not call target method:" + entry);
      }
      return "hook:" + string(s);
    }

    public static void callB1(CH c, A a) {
      c.B1(a);
    }
  }

  public static void test() throws AlbatrossErr {
    C cObj = new C();
    String cs = cObj.string("a");
    assert cs.startsWith("hook:");
    int res = Albatross.hookClass(AH.class);
    assert res == Albatross.CLASS_ALREADY_HOOK;
    assert Albatross.transactionLevel() == 0;
    Albatross.transactionBegin();
    assert Albatross.transactionLevel() == 1;
    Albatross.transactionEnd(true);
    assert Albatross.transactionLevel() == 0;
    for (int i = 0; i < 250; i++) {
      A a = new A();
      res = C.A1(a);
      assert res == 3;
    }
    res = Albatross.hookClass(CH.class);
    assert res == Albatross.CLASS_ALREADY_HOOK;
    for (int i = 0; i < 1250; i++) {
      A a = new A();
      res = C.A1(a);
      assert res == 3;
    }
    CH c = CH.create.newInstance();
    c.B1(new A());
    A convert = Albatross.convert(AH.create.newInstance(), A.class);
    c.B1(convert);
    assert Albatross.transactionLevel() == 0;
  }

  public static void main(boolean hook) {
    new A().b(2);
    assert Albatross.transactionLevel() == 0;
    try {
      int res = Albatross.hookClass(CH.class);
      if (hook)
        assert res > 2;
      else
        assert res == Albatross.CLASS_ALREADY_HOOK;
      test();
    } catch (AlbatrossErr e) {
      throw new RuntimeException(e);
    }

  }
}
