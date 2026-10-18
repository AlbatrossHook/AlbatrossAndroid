package qing.albatross.demo;

import java.util.HashMap;
import java.util.Map;

import qing.albatross.common.Const;
import qing.albatross.nativehook.CModule;
import qing.albatross.nativehook.NativeFunction;

/**
 * Java 经 CModule 使用 stalker C API 做动态代码追踪的端到端验证:
 * C 源码里用 AlbStalkerFollowFunction 追踪注入的宿主 native 函数,
 * 事件回调统计 exec 并判定命中目标函数体, 计数经 C 模块导出函数回读。
 */
public class CModuleStalkerTest {

  /* 复用 CModuleTest 的 native 绑定(entry.c 里已有的 JNI 导出), 不新增 native 入口 */
  private static int nativeTarget(int v) {
    return CModuleTest.nativeTarget(v);
  }

  private static long nativeTargetAddress() {
    return CModuleTest.nativeTargetAddress();
  }

  private static void check(boolean cond, String msg) {
    if (!cond) throw new RuntimeException("check failed: " + msg);
  }

  public static void test() {
    String source =
        "#include <albatross.h>\n"
            + "extern int cmodule_stalk_target(int v);\n"
            + "int alb_trace_exec_count = 0;\n"
            + "int alb_trace_hit_target = 0;\n"
            + "static void alb_trace_on_event(const AlbStalkerEvent *e, void *ud) {\n"
            + "  unsigned long addr;\n"
            + "  (void) ud;\n"
            + "  if (e->type != ALB_STALKER_EVENT_EXEC)\n"
            + "    return;\n"
            + "  alb_trace_exec_count++;\n"
            + "  addr = (unsigned long) (void *) cmodule_stalk_target;\n"
            + "  if (e->exec.location >= addr && e->exec.location < addr + 64)\n"
            + "    alb_trace_hit_target = 1;\n"
            + "}\n"
            + "int alb_trace_run(void) {\n"
            + "  AlbStalkerHandle *s;\n"
            + "  AlbStalkerEventSinkHandle *sink;\n"
            + "  int ret, guard;\n"
            + "  s = AlbStalkerNew();\n"
            + "  sink = AlbStalkerEventSinkNew(ALB_STALKER_EVENT_EXEC, alb_trace_on_event, 0);\n"
            + "  if (s == 0 || sink == 0)\n"
            + "    return -1;\n"
            + "  if (AlbStalkerFollowFunction(s, (void *) cmodule_stalk_target,\n"
            + "        INSTRUMENT_NO_GUARD, 0, sink) != 0) {\n"
            + "    AlbStalkerEventSinkFree(sink);\n"
            + "    AlbStalkerFree(s);\n"
            + "    return -1;\n"
            + "  }\n"
            + "  ret = cmodule_stalk_target(20);\n"
            + "  AlbStalkerUnfollowFunction(s, (void *) cmodule_stalk_target);\n"
            + "  AlbStalkerEventSinkFree(sink);\n"
            + "  for (guard = 0; guard < 1000; guard++)\n"
            + "    if (!AlbStalkerGarbageCollect(s)) break;\n"
            + "  if (AlbStalkerFree(s) != 0)\n"
            + "    return -2;\n"
            + "  return ret;\n"
            + "}\n"
            + "int alb_trace_execs(void) { return alb_trace_exec_count; }\n"
            + "int alb_trace_hit(void) { return alb_trace_hit_target; }\n";

    Map<String, Long> symbols = new HashMap<>();
    symbols.put("cmodule_stalk_target", nativeTargetAddress());
    CModule module = CModule.compile(source, symbols);
    check(module.hasSymbol("alb_trace_run"), "symbol alb_trace_run");

    NativeFunction run = module.getFunction("alb_trace_run", Const.ARG_INT);
    NativeFunction execs = module.getFunction("alb_trace_execs", Const.ARG_INT);
    NativeFunction hit = module.getFunction("alb_trace_hit", Const.ARG_INT);

    // 追踪下调用目标函数: 返回值与直接调用一致
    int traced = (Integer) run.call();
    check(traced == nativeTarget(20), "traced result " + traced + " == direct " + nativeTarget(20));
    check((Integer) execs.call() > 0, "exec events received");
    check((Integer) hit.call() == 1, "target function body traced");

    // 卸载后不再产生事件(计数冻结)
    int before = (Integer) execs.call();
    int direct = nativeTarget(21);
    check(direct == 42, "nativeTarget(21) = " + direct);
    check((Integer) execs.call() == before, "no events after unfollow");

    module.close();
  }
}
