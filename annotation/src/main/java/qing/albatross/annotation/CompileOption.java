package qing.albatross.annotation;

public class CompileOption {
  public static final int COMPILE_OSR = 1;
  public static final int COMPILE_BASELINE = 2;
  public static final int COMPILE_OPTIMIZED = 4;

  public static final int COMPILE_NONE = 0;
  public static final int COMPILE_DECOMPILE = 8;
  public static final int COMPILE_DEFAULT = 0x10;

  public static final int COMPILE_OSR_JIT = COMPILE_OSR | COMPILE_DECOMPILE;
  public static final int COMPILE_BASELINE_JIT = COMPILE_BASELINE | COMPILE_DECOMPILE;
  public static final int COMPILE_OPTIMIZED_JIT = COMPILE_OPTIMIZED | COMPILE_DECOMPILE;
  public static final int COMPILE_DISABLE_AOT = 0x20;
  public static final int COMPILE_DISABLE_JIT = 0x40;
}
