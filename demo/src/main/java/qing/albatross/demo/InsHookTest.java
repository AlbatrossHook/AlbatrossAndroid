package qing.albatross.demo;

import java.lang.reflect.Member;
import java.lang.reflect.Method;

import qing.albatross.core.Albatross;
import qing.albatross.core.InstructionListener;
import qing.albatross.core.InvocationContext;

public class InsHookTest extends InstructionListener {

  int callCount = 0;

  @Override
  public void onEnter(Member method, Object self, int dexPc, InvocationContext invocationContext) {
    assert dexPc == 0;
    Object[] args = invocationContext.getArguments();
    assert args.length == 0;
    assert invocationContext.getStringArguments().length == 0;
    assert invocationContext.getToStringArguments().length == 0;
    assert self == null;
    boolean throwException = false;
    assert invocationContext.getThis(Object.class) == null;
    try {
      invocationContext.setParamInt(0, 1);
    } catch (Throwable ignore) {
      throwException = true;
    }
    assert throwException;
    callCount++;
  }

  static class A {
    static int a(long j, int b) {
      return b + 1;

    }

    String b(double i, String c) {
      return "b:" + c;
    }

    static String c() {
      return "c";
    }
  }


  static void test() throws NoSuchMethodException {
    InsHookTest callback = new InsHookTest();
    Method c = A.class.getDeclaredMethod("c");
    InstructionListener instructionListener = new InstructionListener() {
      @Override
      public void onEnter(Member method, Object self, int dexPc, InvocationContext invocationContext) {
        callback.callCount += 10;
        Object[] args = invocationContext.getArguments();
        assert args.length == 0;
        assert invocationContext.unHook();
      }
    };

    assert Albatross.hookInstruction(c, 0, instructionListener);
    assert Albatross.hookInstruction(c, 0, callback);
    assert !Albatross.hookInstruction(c, 0, callback);
    assert A.c().equals("c");
    assert callback.callCount == 11;
    assert A.c().equals("c");
    assert !instructionListener.unHook();
    assert callback.callCount == 12;
    assert callback.unHook();
    assert !callback.unHook();
    assert A.c().equals("c");
    assert callback.callCount == 12;

    Method a = A.class.getDeclaredMethod("a", long.class, int.class);
    assert A.a(1, 1) == 2;
    instructionListener = new InstructionListener() {
      @Override
      public void onEnter(Member method, Object self, int dexPc, InvocationContext invocationContext) {
        assert method == a;
        assert dexPc == 0;
        Object[] args = invocationContext.getArguments();
        assert invocationContext.getThis() == null;
        assert invocationContext.getStringArguments().length == 0;
        assert args.length == 2;
        assert args[1].getClass() == Integer.class;
        long arg0 = invocationContext.getParamLong(0);
        assert arg0 == 111 || arg0 == 3;
        assert args[0].getClass() == Long.class;
        args = invocationContext.getToStringArguments();
        assert args.length == 2;
        assert args[1].getClass() == Integer.class;
        assert args[0].getClass() == Long.class;
        invocationContext.setParamInt(1, 2);
        boolean throwException = false;
        try {
          invocationContext.setParamInt(0, 1);
        } catch (Throwable ignore) {
          throwException = true;
        }
        assert throwException;
      }
    };
    assert Albatross.hookInstruction(a, 0, instructionListener);
    assert A.a(111, 10) == 3;
    InstructionListener instructionListener1 = new InstructionListener() {
      @Override
      public void onEnter(Member method, Object self, int dexPc, InvocationContext invocationContext) {
        assert method == a;
        assert dexPc == 0;
        int paramInt = invocationContext.getParamInt(1);
        assert paramInt == 2 || paramInt == 111;
        invocationContext.setParamInt(1, 3);
      }
    };
    assert Albatross.hookInstruction(a, 0, instructionListener1);
    int a1 = A.a(111, 10);
    assert a1 == 4 || a1 == 3;
    assert instructionListener.unHook();
    assert instructionListener1.unHook();
    assert A.a(1, 10) == 11;
    Method b = A.class.getDeclaredMethod("b", double.class, String.class);
    assert A.a(2, 1) == 2;
    A i = new A();
    assert i.b(111, "test").equals("b:test");
    double checkValue = 123;

    instructionListener = new InstructionListener() {
      @Override
      public void onEnter(Member method, Object self, int dexPc, InvocationContext invocationContext) {
        assert method == b;
        assert dexPc == 0;
        assert self != null;
        assert self == i;
        Object[] args = invocationContext.getArguments();
        assert args.length == 3;
        assert args[1].getClass() == Double.class;
        assert args[1].equals(checkValue);
        assert args[0] == self;
        assert invocationContext.getThis(A.class) == self;
        assert "test".equals(invocationContext.getParamObject(2, String.class));
        invocationContext.setParamObject(2, "hooked");
        assert "hooked".equals(invocationContext.getParamObject(2, String.class));
        boolean throwException = false;
        try {
          invocationContext.getThis(InsHookTest.class);
        } catch (Throwable ignore) {
          throwException = true;
        }
        assert throwException;

        args = invocationContext.getToStringArguments();
        assert args.length == 3;
        args = invocationContext.getStringArguments();
        assert "hooked".equals(args[2]);
      }
    };
    assert Albatross.hookInstruction(b, 0, instructionListener);
    assert i.b(checkValue, "test").equals("b:hooked");
//    assert Albatross.hookInstruction(b, 0, (Member method, Object self, int dexPc, InvocationContext invocationContext) -> {
//      invocationContext.setParamObject(0, "z");
//    }) == null;
    assert i.b(checkValue, "test").equals("b:hooked");
    instructionListener.unHook();
    assert i.b(122, "test").equals("b:test");
  }

}
