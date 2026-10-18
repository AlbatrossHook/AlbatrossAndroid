package albatross.demo;

/**
 * OceanTraceTest 的追踪靶子: 与 {@link TraceTimeoutTarget} 同理, 放在
 * qing.albatross. 白名单前缀之外(OceanTracker.init 会 addWhitePackage("qing.albatross.")),
 * 保证默认 trace scope 下会被追踪到。
 *
 * 事件源是 spin 内层高频调用的 {@link #bump}(CALL 事件按方法进入计), 单次长循环
 * 保证 syncTrace 拉回解释器后事件持续派发(短调用循环会被 JIT 重新编译而丢事件)。
 */
public class OceanTraceTarget {

  public static int bump(int n) {
    return n + 1;
  }

  /** keyword 命中靶子: 参数含标记串时被 KeyWordSinker 命中并输出一行 */
  public static void keywordTarget(String mark) {
  }

  /** string 命中靶子: String 参数被 StringSinker 按小写包含匹配 */
  public static void stringTarget(String text) {
  }

  public static int spin(int loops) {
    int n = 0;
    for (int i = 0; i < loops; i++)
      n = bump(n);
    return n;
  }
}
