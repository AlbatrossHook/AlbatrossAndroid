package albatross.demo;

/**
 * JavaMethodTraceTest / run_java_method_trace_smoke.py 的追踪靶子:
 * 放在非白名单包(qing.albatross. 之外)里, 保证默认 trace scope 下事件可达(同 TraceTimeoutTarget)。
 *
 * run 的循环体只做 Java→Java 调用(CALL 事件来源); recurse 每层都调用自身, 用来验证
 * "只在最外层 arm / 最外层退出 disarm" 的深度配对。
 */
public class JavaMethodTraceTarget {

  public static int leaf(int n) {
    return n + 1;
  }

  /** 目标方法: 方法体内一串 Java→Java 调用(单轮 trace 的 CALL 事件来源) */
  public static int run(int loops) {
    int n = 0;
    for (int i = 0; i < loops; i++)
      n = leaf(n);
    return n;
  }

  /** 递归靶子: 每层调用自身, 只在最外层 arm / 最外层退出 disarm */
  public static int recurse(int depth) {
    if (depth <= 0)
      return leaf(0);
    return leaf(recurse(depth - 1));
  }
}
