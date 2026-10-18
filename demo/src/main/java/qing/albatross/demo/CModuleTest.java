package qing.albatross.demo;

import java.util.HashMap;
import java.util.Map;

import qing.albatross.common.Const;
import qing.albatross.nativehook.CModule;
import qing.albatross.nativehook.CModuleCompileException;
import qing.albatross.nativehook.NativeFunction;

/**
 * CModule(进程内 tcc 动态编译 C)端到端验证:
 * 编译/外部符号注入/C 侧安装 instrument hook/NativeFunction 各类型参数返回/
 * 编译错误路径。
 */
public class CModuleTest {

  static native int nativeTarget(int v);

  static native long nativeTargetAddress();

  static native long injectedMulAddress();

  public static void test() {
    String source =
        "#include <albatross.h>\n"
            + "#include <string.h>\n"
            + "#include <stdint.h>\n"
            + "extern int cmodule_injected_mul(int a, int b);\n"
            + "static long listener_id = -1;\n"
            + "static int enter_count = 0;\n"
            + "static int last_arg0 = -1;\n"
            + "static void on_enter(InvocationContext *ctx, void *data) {\n"
            + "  (void) data;\n"
            + "  enter_count++;\n"
            + "  last_arg0 = (int) AlbatrossGetNthArgument(ctx, 0);\n"
            + "}\n"
            + "int alb_install(void *target) {\n"
            + "  listener_id = AlbatrossHookInstrument(target, on_enter, 0, 0, 0, 0);\n"
            + "  return listener_id >= 0 ? 1 : 0;\n"
            + "}\n"
            + "int alb_uninstall(void *target) {\n"
            + "  if (listener_id < 0) return 0;\n"
            + "  int r = AlbatrossUnHookInstrument(target, listener_id);\n"
            + "  listener_id = -1;\n"
            + "  return r;\n"
            + "}\n"
            + "int alb_count(void) { return enter_count; }\n"
            + "int alb_last_arg0(void) { return last_arg0; }\n"
            + "int64_t alb_add_i64(int64_t v) { return v + (int64_t) strlen(\"abcd\"); }\n"
            + "int alb_add_i32(int a, int b) { return a * 10 + b; }\n"
            + "int alb_strlen(const char *s) { return (int) strlen(s); }\n"
            + "double alb_half(double v) { return v / 2; }\n"
            + "int alb_injected(int a, int b) { return cmodule_injected_mul(a, b) + 1; }\n"
            + "int alb_global = 30;\n"
            + "void alb_log_test(void) { AlbLog(\"CModuleTest\", \"hello from cmodule %d\", 42); }\n";

    Map<String, Long> symbols = new HashMap<>();
    symbols.put("cmodule_injected_mul", injectedMulAddress());
    CModule module = CModule.compile(source, symbols);
    check(module.hasSymbol("alb_add_i64"), "symbol alb_add_i64");
    check(module.getAddress("alb_global") != 0, "global variable symbol");

    // C 侧安装 instrument hook, Java 经 JNI 触发目标函数
    NativeFunction install = module.getFunction("alb_install", Const.ARG_INT, Const.ARG_PTR);
    check((Integer) install.call(nativeTargetAddress()) == 1, "install hook");
    int result = nativeTarget(21);
    check(result == 42, "nativeTarget(21) = " + result);
    NativeFunction count = module.getFunction("alb_count", Const.ARG_INT);
    NativeFunction lastArg0 = module.getFunction("alb_last_arg0", Const.ARG_INT);
    check((Integer) count.call() == 1, "enter count");
    check((Integer) lastArg0.call() == 21, "captured arg0");

    // NativeFunction: int64/int/string/double 各类型参数与返回
    NativeFunction addI64 = module.getFunction("alb_add_i64", Const.ARG_LONG, Const.ARG_LONG);
    check((Long) addI64.call(100L) == 104L, "alb_add_i64(100)");
    NativeFunction addI32 = module.getFunction("alb_add_i32", Const.ARG_INT, Const.ARG_INT, Const.ARG_INT);
    check((Integer) addI32.call(3, 7) == 37, "alb_add_i32(3,7)");
    NativeFunction strlen = module.getFunction("alb_strlen", Const.ARG_INT, Const.ARG_STR);
    check((Integer) strlen.call("albatross") == 9, "alb_strlen");
    NativeFunction half = module.getFunction("alb_half", Const.ARG_DOUBLE, Const.ARG_DOUBLE);
    check((Double) half.call(5.0) == 2.5, "alb_half(5)");

    // 注入符号: C 侧调用宿主函数
    NativeFunction injected = module.getFunction("alb_injected", Const.ARG_INT, Const.ARG_INT, Const.ARG_INT);
    check((Integer) injected.call(6, 7) == 43, "injected symbol call");

    // C 侧日志(logcat 验证 AlbLog 走 __android_log_print)
    module.getFunction("alb_log_test", Const.ARG_VOID).call();

    // 卸载 hook 后计数不再增长, 关闭模块
    NativeFunction uninstall = module.getFunction("alb_uninstall", Const.ARG_INT, Const.ARG_PTR);
    check((Integer) uninstall.call(nativeTargetAddress()) == 1, "uninstall hook");
    result = nativeTarget(5);
    check(result == 10, "nativeTarget(5) after uninstall = " + result);
    check((Integer) count.call() == 1, "count unchanged after uninstall");

    // 编译缓存: 相同 key 再次 compile 共享底层模块(符号地址相同), 引用独立关闭
    CModule module2 = CModule.compile(source, symbols);
    check(module2.getAddress("alb_add_i64") == module.getAddress("alb_add_i64"),
        "compile cache shares symbols");
    check((Integer) module2.getFunction("alb_count", Const.ARG_INT).call() == 1,
        "cached module keeps state");
    module2.close();
    // 缓存命中关闭后原引用仍可用
    check((Integer) count.call() == 1, "original handle alive after cached close");
    module.close();

    // 参数类型不匹配: 抛 IllegalArgumentException 而非 native 崩溃
    boolean typeThrew = false;
    try {
      addI32.call("not a number", 1);
    } catch (IllegalArgumentException e) {
      typeThrew = true;
    }
    check(typeThrew, "argument type mismatch throws");

    // 注入表含 null key: 被过滤而非崩溃
    Map<String, Long> badSymbols = new HashMap<>();
    badSymbols.put(null, 1L);
    CModule bad = CModule.compile("int alb_null_key(void) { return 1; }", badSymbols);
    check(bad.getAddress("alb_null_key") != 0, "null key symbol filtered");
    bad.close();

    // 编译失败: 抛异常且带 tcc 错误文本
    boolean threw = false;
    try {
      CModule.compile("int broken( {");
    } catch (CModuleCompileException e) {
      threw = e.getMessage() != null && !e.getMessage().isEmpty();
    }
    check(threw, "compile error exception");
  }

  static void check(boolean cond, String what) {
    if (!cond)
      throw new IllegalStateException("CModuleTest failed: " + what);
  }
}
