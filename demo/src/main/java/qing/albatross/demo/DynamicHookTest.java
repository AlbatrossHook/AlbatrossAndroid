package qing.albatross.demo;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import qing.albatross.core.Albatross;
import qing.albatross.core.CallFrame;
import qing.albatross.core.MethodCallHook;
import qing.albatross.core.MethodHookCallback;

/**
 * 免签名动态 hook（trampoline + native bridge 路线）验证。
 * 断言使用异常而非 assert（新 ART 上 assert 不生效，见 AGENTS 说明）。
 */
public class DynamicHookTest {

  static void check(boolean cond, String msg) {
    if (!cond)
      throw new AssertionError(msg);
  }

  /**
   * 用例内所有 hook 句柄统一登记：用例退出（含断言失败）时在 finally 里全部卸载。
   * 否则失败用例会把 hook 留给后面的用例，后续用例里陈旧回调的断言会变成莫名其妙的级联失败
   * （unhooked/DynamicHookTest 的 basic→invokeOriginal/chain/multiHook 就是这样串起来的）。
   */
  private static MethodCallHook track(List<MethodCallHook> handles, MethodCallHook h) {
    handles.add(h);
    return h;
  }

  static class T {
    static int count;

    int add(int a, int b) {
      count++;
      return a + b;
    }

    // 超过寄存器参数个数，验证栈参数布局（arm64: >7 整数参数；x86_64: >5）
    static long stackArgs(long a, int b, long c, int d, long e, int f, long g, int h, long i, int j) {
      return a + b + c + d + e + f + g + h + i + j;
    }

    static double mixedFp(double a, float b, double c, float d, double e, float f, double g, float h, double i, float j) {
      return a + b + c + d + e + f + g + h + i + j;
    }

    String concat(String s, List<String> l) {
      return s + l.size();
    }

    static long wide(long v) {
      return v * 2;
    }

    double dbl(double v) {
      return v * 2;
    }

    float flt(float v) {
      return v * 2;
    }

    static boolean bool(boolean v) {
      return !v;
    }

    static char ch(char v) {
      return (char) (v + 1);
    }

    static byte bt(byte v) {
      return (byte) (v + 1);
    }

    static short sh(short v) {
      return (short) (v + 1);
    }

    void nop(int v) {
      count += v;
    }

    static int boom() {
      throw new IllegalStateException("boom");
    }
  }

  static void testBasic() throws Exception {
    T.count = 0; // 静态计数复位: 同一进程里重复跑本用例(TestMain 的 hooked/unhooked 两相)时保持独立
    T t = new T();
    // 实例方法 + int 返回 + 改参数
    Method add = T.class.getDeclaredMethod("add", int.class, int.class);
    final int[] calls = {0};
    List<MethodCallHook> handles = new ArrayList<>();
    try {
      MethodCallHook hook = track(handles, Albatross.hookMethod(add, new MethodHookCallback() {
      @Override
      public boolean beforeCall(CallFrame frame) {
        calls[0]++;
        check(frame.getParamInt(0) == 10, "arg0");
        check(frame.getParamInt(1) == 20, "arg1");
        check(frame.getThis() instanceof T, "this");
        frame.setParamInt(1, 30);
        return true;
      }

      @Override
      public void afterCall(CallFrame frame) {
        check(((Integer) frame.getResult()) == 40, "after result");
      }
    }));
      check(hook != null, "hook installed");
      int got = t.add(10, 20);
      Albatross.log("add got=" + got);
      check(got == 40, "modified arg");
      check(calls[0] == 1, "before called");
      check(T.count == 1, "original executed");
      // 跳过原方法 + setResult
      hook.unHook();
      check(t.add(1, 2) == 3, "unhooked");
      check(T.count == 2, "original after unhook");

      MethodCallHook skip = track(handles, Albatross.hookMethod(add, new MethodHookCallback() {
        @Override
        public boolean beforeCall(CallFrame frame) {
          frame.setResult(999);
          return false;
        }
      }));
      int before = T.count;
      check(t.add(5, 6) == 999, "skipped result");
      check(T.count == before, "original skipped");
      skip.unHook();

      // beforeCall 返回 false + 默认值
      MethodCallHook skip2 = track(handles, Albatross.hookMethod(add, new MethodHookCallback() {
        @Override
        public boolean beforeCall(CallFrame frame) {
          return false;
        }
      }));
      check(t.add(5, 6) == 0, "skipped default");
      skip2.unHook();
    } finally {
      // 断言失败也必须把 hook 卸干净(unHook 幂等), 否则会污染后面的用例
      for (MethodCallHook h : handles)
        h.unHook();
    }
  }

  static void testStackArgs() throws Exception {
    // 栈参数读取与写回（校准 ABI 布局的关键用例）
    Method m = T.class.getDeclaredMethod("stackArgs",
        long.class, int.class, long.class, int.class, long.class, int.class, long.class, int.class, long.class, int.class);
    final boolean[] seen = {false};
    MethodCallHook hook = Albatross.hookMethod(m, new MethodHookCallback() {
      @Override
      public boolean beforeCall(CallFrame frame) {
        seen[0] = true;
        Object[] args = frame.getArguments();
        check(args.length == 10, "args len");
        long[] expectL = {100, 200, 300, 400, 900};
        int li = 0;
        for (int i = 0; i < 10; i++) {
          Object a = args[i];
          if ((i & 1) == 0) {
            check(a instanceof Long && ((Long) a) == expectL[li++], "stack long " + i + "=" + a);
          } else {
            check(a instanceof Integer && ((Integer) a) == i + 1, "stack int " + i + "=" + a);
          }
        }
        check(frame.getParamLong(8) == 900, "typed long");
        check(frame.getParamInt(9) == 10, "typed int");
        frame.setParamLong(8, 901);
        return true;
      }
    });
    check(T.stackArgs(100, 2, 200, 4, 300, 6, 400, 8, 900, 10) == 100 + 2 + 200 + 4 + 300 + 6 + 400 + 8 + 901 + 10,
        "stack writeback");
    check(seen[0], "stack hook called");
    hook.unHook();
  }

  static void testFp() throws Exception {
    Method m = T.class.getDeclaredMethod("mixedFp",
        double.class, float.class, double.class, float.class, double.class, float.class, double.class, float.class, double.class, float.class);
    MethodCallHook hook = Albatross.hookMethod(m, new MethodHookCallback() {
      @Override
      public boolean beforeCall(CallFrame frame) {
        check(frame.getParamDouble(0) == 1.5, "d0");
        check(frame.getParamFloat(1) == 2.5f, "f1");
        check(frame.getParamDouble(8) == 9.5, "d8");
        check(frame.getParamFloat(9) == 10.5f, "f9");
        frame.setParamDouble(8, 19.5);
        return true;
      }
    });
    double expect = 1.5 + 2.5f + 3.5 + 4.5f + 5.5 + 6.5f + 7.5 + 8.5f + 19.5 + 10.5f;
    check(T.mixedFp(1.5, 2.5f, 3.5, 4.5f, 5.5, 6.5f, 7.5, 8.5f, 9.5, 10.5f) == expect, "fp sum");
    hook.unHook();

    Method d = T.class.getDeclaredMethod("dbl", double.class);
    MethodCallHook hd = Albatross.hookMethod(d, new MethodHookCallback() {
      @Override
      public boolean beforeCall(CallFrame frame) {
        check(frame.getParamDouble(0) == 3.25, "dbl arg");
        return true;
      }

      @Override
      public void afterCall(CallFrame frame) {
        check(((Double) frame.getResult()) == 6.5, "dbl result");
        frame.setResult(1.25);
      }
    });
    check(new T().dbl(3.25) == 1.25, "dbl after modify");
    hd.unHook();

    Method f = T.class.getDeclaredMethod("flt", float.class);
    MethodCallHook hf = Albatross.hookMethod(f, new MethodHookCallback() {
      @Override
      public boolean beforeCall(CallFrame frame) {
        check(frame.getParamFloat(0) == 1.5f, "flt arg");
        return true;
      }
    });
    check(new T().flt(1.5f) == 3.0f, "flt result");
    hf.unHook();
  }

  static void testReturnTypes() throws Exception {
    Method wl = T.class.getDeclaredMethod("wide", long.class);
    MethodCallHook h1 = Albatross.hookMethod(wl, new MethodHookCallback() {
      @Override
      public boolean beforeCall(CallFrame frame) {
        check(frame.getParamLong(0) == 0x123456789L, "long arg");
        return true;
      }
    });
    check(T.wide(0x123456789L) == 0x2468acf12L, "long ret");
    h1.unHook();

    Method b = T.class.getDeclaredMethod("bool", boolean.class);
    MethodCallHook h2 = Albatross.hookMethod(b, new MethodHookCallback() {
      @Override
      public boolean beforeCall(CallFrame frame) {
        check(frame.getParamBoolean(0), "bool arg");
        return true;
      }
    });
    check(T.bool(true) == false, "bool ret");
    h2.unHook();

    Method c = T.class.getDeclaredMethod("ch", char.class);
    MethodCallHook h3 = Albatross.hookMethod(c, new MethodHookCallback() {
      @Override
      public boolean beforeCall(CallFrame frame) {
        check(frame.getParamChar(0) == 'a', "char arg");
        return true;
      }
    });
    check(T.ch('a') == 'b', "char ret");
    h3.unHook();

    Method by = T.class.getDeclaredMethod("bt", byte.class);
    MethodCallHook h4 = Albatross.hookMethod(by, new MethodHookCallback() {
      @Override
      public boolean beforeCall(CallFrame frame) {
        check(frame.getParamByte(0) == 3, "byte arg");
        return true;
      }
    });
    check(T.bt((byte) 3) == 4, "byte ret");
    h4.unHook();

    Method sh = T.class.getDeclaredMethod("sh", short.class);
    MethodCallHook h5 = Albatross.hookMethod(sh, new MethodHookCallback() {
      @Override
      public boolean beforeCall(CallFrame frame) {
        check(frame.getParamShort(0) == 7, "short arg");
        return true;
      }
    });
    check(T.sh((short) 7) == 8, "short ret");
    h5.unHook();

    // void 返回
    T.count = 0;
    Method np = T.class.getDeclaredMethod("nop", int.class);
    MethodCallHook h6 = Albatross.hookMethod(np, new MethodHookCallback() {
      @Override
      public boolean beforeCall(CallFrame frame) {
        frame.setParamInt(0, 5);
        return true;
      }
    });
    new T().nop(1);
    check(T.count == 5, "void exec with modified arg");
    h6.unHook();
  }

  static void testObject() throws Exception {
    Method m = T.class.getDeclaredMethod("concat", String.class, List.class);
    final List<String> data = new ArrayList<>();
    data.add("x");
    MethodCallHook hook = Albatross.hookMethod(m, new MethodHookCallback() {
      @Override
      public boolean beforeCall(CallFrame frame) {
        check("s".equals(frame.getParamObject(0)), "obj arg");
        Object o = frame.getParamObject(1);
        check(o == data, "list identity");
        Object[] args = frame.getArguments();
        check("s".equals(args[0]) && args.length == 2, "boxed args");
        frame.setParamObject(0, "S-");
        return true;
      }

      @Override
      public void afterCall(CallFrame frame) {
        check("S-1".equals(frame.getResult()), "obj result");
      }
    });
    check("S-1".equals(new T().concat("s", data)), "obj flow");
    hook.unHook();

    // object 返回值替换
    MethodCallHook h2 = Albatross.hookMethod(m, new MethodHookCallback() {
      @Override
      public boolean beforeCall(CallFrame frame) {
        frame.setResult("replaced");
        return false;
      }
    });
    check("replaced".equals(new T().concat("s", data)), "obj skip result");
    h2.unHook();
  }

  static void testInvokeOriginal() throws Exception {
    Method m = T.class.getDeclaredMethod("add", int.class, int.class);
    List<MethodCallHook> handles = new ArrayList<>();
    try {
      MethodCallHook hook = track(handles, Albatross.hookMethod(m, new MethodHookCallback() {
        @Override
        public boolean beforeCall(CallFrame frame) {
          int raw = frame.invokeInt();
          frame.setResult(raw + 1);
          return false;
        }
      }));
      check(new T().add(7, 8) == 16, "invokeOriginal");
      hook.unHook();
    } finally {
      for (MethodCallHook h : handles)
        h.unHook();
    }
  }

  /**
   * invokeOriginal 的返回值编组与宽/浮点类型：native 直调 ArtMethod::Invoke 后，
   * 对象返回值经 JNIEnvExt::NewLocalRef、基本类型经包装类 valueOf 交回 Java。
   * 逐个返回类型验证经 hook 取的 invokeOriginal 结果与直接调用一致。
   */
  static void testInvokeOriginalTypes() throws Exception {
    T t = new T();
    List<MethodCallHook> handles = new ArrayList<>();
    try {
      Method ml = T.class.getDeclaredMethod("wide", long.class);
      track(handles, Albatross.hookMethod(ml, new MethodHookCallback() {
        @Override
        public boolean beforeCall(CallFrame frame) {
          frame.setResult(frame.invokeLong());
          return false;
        }
      }));
      check(T.wide(1234567890123L) == 2469135780246L, "invokeOriginal long");

      Method md = T.class.getDeclaredMethod("dbl", double.class);
      track(handles, Albatross.hookMethod(md, new MethodHookCallback() {
        @Override
        public boolean beforeCall(CallFrame frame) {
          frame.setResult(frame.invokeDouble());
          return false;
        }
      }));
      check(t.dbl(2.5) == 5.0, "invokeOriginal double");

      Method mf = T.class.getDeclaredMethod("flt", float.class);
      track(handles, Albatross.hookMethod(mf, new MethodHookCallback() {
        @Override
        public boolean beforeCall(CallFrame frame) {
          frame.setResult(frame.invokeFloat());
          return false;
        }
      }));
      check(t.flt(1.5f) == 3.0f, "invokeOriginal float");

      Method mb = T.class.getDeclaredMethod("bool", boolean.class);
      track(handles, Albatross.hookMethod(mb, new MethodHookCallback() {
        @Override
        public boolean beforeCall(CallFrame frame) {
          frame.setResult(frame.invokeBoolean());
          return false;
        }
      }));
      check(!T.bool(true), "invokeOriginal boolean");

      Method mc = T.class.getDeclaredMethod("ch", char.class);
      track(handles, Albatross.hookMethod(mc, new MethodHookCallback() {
        @Override
        public boolean beforeCall(CallFrame frame) {
          frame.setResult(frame.invokeChar());
          return false;
        }
      }));
      check(T.ch('a') == 'b', "invokeOriginal char");

      Method mo = T.class.getDeclaredMethod("concat", String.class, List.class);
      track(handles, Albatross.hookMethod(mo, new MethodHookCallback() {
        @Override
        public boolean beforeCall(CallFrame frame) {
          frame.setResult(frame.invokeObject());
          return false;
        }
      }));
      List<String> one = new ArrayList<>();
      one.add("x");
      check("S-1".equals(t.concat("S-", one)), "invokeOriginal object");

      // void：结果应为 null，且原方法确实执行（count 增长）
      Method mn = T.class.getDeclaredMethod("nop", int.class);
      track(handles, Albatross.hookMethod(mn, new MethodHookCallback() {
        @Override
        public boolean beforeCall(CallFrame frame) {
          check(frame.invokeOriginal() == null, "void invokeOriginal null");
          return false;
        }
      }));
      T.count = 0;
      t.nop(7);
      check(T.count == 7, "invokeOriginal void ran original");
    } finally {
      for (MethodCallHook h : handles)
        h.unHook();
    }
  }

  static void testException() throws Exception {
    Method m = T.class.getDeclaredMethod("boom");
    final boolean[] after = {false};
    MethodCallHook hook = Albatross.hookMethod(m, new MethodHookCallback() {
      @Override
      public boolean beforeCall(CallFrame frame) {
        return true;
      }

      @Override
      public void afterCall(CallFrame frame) {
        after[0] = true;
        check(frame.getThrowable() instanceof IllegalStateException, "origin throwable");
      }
    });
    try {
      T.boom();
      check(false, "should throw");
    } catch (IllegalStateException e) {
      check("boom".equals(e.getMessage()), "ex msg");
    }
    check(after[0], "afterCall ran");
    hook.unHook();

    // setThrowable 跳过原方法
    final RuntimeException mine = new RuntimeException("mine");
    MethodCallHook h2 = Albatross.hookMethod(m, new MethodHookCallback() {
      @Override
      public boolean beforeCall(CallFrame frame) {
        frame.setThrowable(mine);
        return true;
      }
    });
    try {
      T.boom();
      check(false, "should throw 2");
    } catch (RuntimeException e) {
      check(e == mine, "custom throwable");
    }
    h2.unHook();

    // 回调自身抛异常：跳过原方法并传播
    MethodCallHook h3 = hookThrowingCallback(m);
    try {
      T.boom();
      check(false, "should throw 3");
    } catch (ArithmeticException ignore) {
    }
    h3.unHook();
  }

  static MethodCallHook hookThrowingCallback(Method m) {
    return Albatross.hookMethod(m, new MethodHookCallback() {
      @Override
      public boolean beforeCall(CallFrame frame) {
        throw new ArithmeticException("cb");
      }
    });
  }

  static void testChain() throws Exception {
    Method m = T.class.getDeclaredMethod("add", int.class, int.class);
    final StringBuilder order = new StringBuilder();
    List<MethodCallHook> handles = new ArrayList<>();
    try {
      MethodCallHook h1 = track(handles, Albatross.hookMethod(m, new MethodHookCallback() {
        @Override
        public boolean beforeCall(CallFrame frame) {
          order.append("b1");
          return true;
        }

        @Override
        public void afterCall(CallFrame frame) {
          order.append("a1");
        }
      }));
      MethodCallHook h2 = track(handles, Albatross.hookMethod(m, new MethodHookCallback() {
        @Override
        public boolean beforeCall(CallFrame frame) {
          order.append("b2");
          return true;
        }

        @Override
        public void afterCall(CallFrame frame) {
          order.append("a2");
        }
      }));
      new T().add(1, 1);
      check(order.toString().contains("b1") && order.toString().contains("b2")
          && order.toString().contains("a1") && order.toString().contains("a2"), "chain " + order);
      h2.unHook();
      h1.unHook();
    } finally {
      for (MethodCallHook h : handles)
        h.unHook();
    }
  }

  // ---- 同一方法多次 hook：回调链聚合、单卸、invokeOriginal 纯原始 ----

  static MethodHookCallback lastHook;

  static void testMultiHook() throws Exception {
    T t = new T();
    Method m = T.class.getDeclaredMethod("add", int.class, int.class);
    MethodHookCallback cb1 = new MethodHookCallback() {
      @Override
      public boolean beforeCall(CallFrame frame) {
        frame.setParamInt(1, frame.getParamInt(1) * 10);
        return true;
      }
    };
    lastHook = new MethodHookCallback() {
      @Override
      public boolean beforeCall(CallFrame frame) {
        frame.setResult(frame.invokeInt() + 5);
        return false;
      }
    };
    List<MethodCallHook> handles = new ArrayList<>();
    try {
      MethodCallHook h1 = track(handles, Albatross.hookMethod(m, cb1));
      MethodCallHook h2 = track(handles, Albatross.hookMethod(m, lastHook));
      check(h1 == h2, "aggregated handle");
      check(t.add(2, 3) == 2 + 30 + 5, "stacked hooks");
      // 单卸 lastHook（跳层），cb1 仍在
      check(h1.unHook(lastHook), "remove one");
      check(t.add(2, 3) == 2 + 30, "one left");
      h1.unHook();
      check(t.add(2, 3) == 5, "restored");
      // 卸载后的句柄不可复用
      boolean threw = false;
      try {
        h1.addHook(cb1);
      } catch (IllegalStateException ignore) {
        threw = true;
      }
      check(threw, "dead handle rejected");
      // 重新 hook 应得到新句柄
      MethodCallHook h3 = track(handles, Albatross.hookMethod(m, cb1));
      check(h3 != null && h3 != h1, "fresh handle");
      check(t.add(1, 1) == 1 + 10, "rehook works");
      h3.unHook();
    } finally {
      for (MethodCallHook h : handles)
        h.unHook();
    }
  }

  public static void test() throws Exception {
    TestRunner.run("basic", DynamicHookTest::testBasic);
    TestRunner.run("stackArgs", DynamicHookTest::testStackArgs);
    TestRunner.run("fp", DynamicHookTest::testFp);
    TestRunner.run("returnTypes", DynamicHookTest::testReturnTypes);
    TestRunner.run("object", DynamicHookTest::testObject);
    TestRunner.run("invokeOriginal", DynamicHookTest::testInvokeOriginal);
    TestRunner.run("invokeOriginalTypes", DynamicHookTest::testInvokeOriginalTypes);
    TestRunner.run("exception", DynamicHookTest::testException);
    TestRunner.run("chain", DynamicHookTest::testChain);
    TestRunner.run("multiHook", DynamicHookTest::testMultiHook);
  }
}
