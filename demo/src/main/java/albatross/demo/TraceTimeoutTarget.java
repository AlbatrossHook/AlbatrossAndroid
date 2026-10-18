package albatross.demo;

/**
 * TraceTimeoutTest 的追踪靶子: 放在非白名单包(qing.albatross. 之外)里,
 * 保证默认 trace scope(kAll)下会被追踪到。
 *
 * spin 的循环体只做 Java→Java 调用(CALL 事件来源), 循环次数给足,
 * 使插桩执行时间远超超时窗口 —— 窗口到期一定发生在循环进行中。
 */
public class TraceTimeoutTarget {

  public static int bump(int n) {
    return n + 1;
  }

  public static int spin(int loops) {
    int n = 0;
    for (int i = 0; i < loops; i++)
      n = bump(n);
    return n;
  }
}
