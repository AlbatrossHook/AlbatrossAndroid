package qing.albatross.demo;

import qing.albatross.annotation.FuncBackup;
import qing.albatross.annotation.Symbol;
import qing.albatross.annotation.TargetLibrary;
import qing.albatross.annotation.Word64;
import qing.albatross.nativehook.AlbNative;

/**
 * libc 纯函数的 backup 绑定: 无副作用、返回值可精确断言,
 * 用于验证 backup 跳板的各类参数 marshalling(无参/int/String)。
 */
@TargetLibrary("c")
public class LibcPure {

  @Symbol
  static long abs;

  @Symbol
  static long getpid;

  @Symbol
  static long strlen;

  @FuncBackup
  public static native int abs(int v);

  @FuncBackup
  public static native int getpid();

  @FuncBackup
  // 32 位上 size_t 是单机器字, 不标 @Word64 会按 64 位读返回、高位取到被调方残留
  @Word64
  public static native long strlen(String s);

  static {
    AlbNative.hookNative();
  }
}
