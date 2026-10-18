package qing.albatross.demo;

/**
 * 压力/浸泡类用例的时长取值：默认取"快速回归"档（够暴露竞态即可），
 * 环境变量 {@code ALBATROSS_STRESS=full} 时取历史长浸泡档（发版前/长跑用）。
 *
 * <p>背景：这类用例的时长是"浸泡窗口"而不是被测行为本身——GC 锤、引用身份锤
 * 每秒都在做同一件事，缩短窗口只降低单位时间里的命中次数，不减覆盖维度。
 * 默认档把全套 java 用例从 ~7 分钟（414s）压到 ~70 秒（69s）（原来 60s+10s+10s 的浸泡
 * 占了九成），完整档用 {@code ALBATROSS_STRESS=full script/run_java_tests.sh}
 * 跑（仅进程内宿主链路能传环境变量；APK 广播链路始终是默认档）。
 *
 * <p>坑见 doc/pitfalls/pitfall-java-suite-duration-20261010.md
 */
public final class StressSoak {

  private static final boolean FULL = "full".equals(System.getenv("ALBATROSS_STRESS"));

  /** 默认档为 {@code fastMs}, ALBATROSS_STRESS=full 时为 {@code fullMs}。 */
  public static int ms(int fastMs, int fullMs) {
    return FULL ? fullMs : fastMs;
  }

  private StressSoak() {
  }
}
