package qing.albatross.demo;

import java.util.ArrayList;
import java.util.List;

import qing.albatross.annotation.FuncBackup;
import qing.albatross.annotation.FuncHook;
import qing.albatross.annotation.TargetLibrary;
import qing.albatross.exception.AlbatrossErr;
import qing.albatross.nativehook.AlbNative;
import qing.albatross.nativehook.DlInfo;

public class NativeHookTest {

  @TargetLibrary("api")
  static class ApiHooker {


    @FuncBackup
    static native int getInt$Backup(int v);

    @FuncHook
    static int getInt(int v) {
      return getInt$Backup(v + 100);
    }
  }

  // long 参数/返回(32 位上占双槽)
  @TargetLibrary("api")
  static class LongHooker {

    @FuncBackup
    static native long addLong$Backup(long a, long b);

    @FuncHook
    static long addLong(long a, long b) {
      return addLong$Backup(a + 1, b + 2);
    }
  }

  // DEBUG: 纯 backup 的 64 位返回
  @TargetLibrary("api")
  static class LongBackupOnly {

    @FuncBackup
    static native long addLong$Backup(long a, long b);
  }

  // String 参数/返回(返回值走线程级复用缓冲)
  @TargetLibrary("api")
  static class GreetHooker {

    @FuncBackup
    static native String greet$Backup(String name);

    @FuncHook
    static String greet(String name) {
      return greet$Backup(name) + "!";
    }
  }

  // 10 个 int 参数: 超出寄存器参数数, 验证编组的栈回退
  @TargetLibrary("api")
  static class SumHooker {

    @FuncBackup
    static native int sum10$Backup(int a1, int a2, int a3, int a4, int a5, int a6, int a7, int a8, int a9, int a10);

    @FuncHook
    static int sum10(int a1, int a2, int a3, int a4, int a5, int a6, int a7, int a8, int a9, int a10) {
      return sum10$Backup(a1 + 1, a2 + 1, a3 + 1, a4 + 1, a5 + 1, a6 + 1, a7 + 1, a8 + 1, a9 + 1, a10 + 1);
    }
  }

  // void 返回: 也必须跳过原函数(计数器只加替换路径的值)
  @TargetLibrary("api")
  static class BumpHooker {

    @FuncBackup
    static native void bump$Backup(int delta);

    @FuncHook
    static void bump(int delta) {
      bump$Backup(delta * 2);
    }
  }

  // 替换方法抛异常: 清除异常并放行原函数兜底
  @TargetLibrary("api")
  static class ThrowHooker {

    @FuncBackup
    static native int twice$Backup(int v);

    @FuncHook
    static int twice(int v) {
      throw new IllegalStateException("func hook replacement boom");
    }
  }

  // C 层直调 libapi.so 的目标函数(entry.c), 均经过 FuncHook 拦截点
  static native int getInt(int v);

  static native long addLong(long a, long b);

  static native long getAddLongArg(int idx);

  static native String greet(String name);

  static native int sum10(int a1, int a2, int a3, int a4, int a5, int a6, int a7, int a8, int a9, int a10);

  static native int getSum10Arg(int idx);

  static native void bump(int delta);

  static native int getCounter();

  static native int twice(int v);


  static class LibcHooker {

    static native int open(String path, int mode, int p);
  }

  static void check(boolean cond, String message) {
    if (!cond)
      throw new AssertionError(message);
  }

  public static void test() throws AlbatrossErr {
    List<String> symbols = new ArrayList<>();
    AlbNative.enumerateModules((path, addr, offset, idx) -> {
      symbols.add(path + ":" + addr + ":" + offset);
      return true;
    });
    check(!symbols.isEmpty(), "enumerateModules empty");
    symbols.clear();
    DlInfo lib = AlbNative.openLib("libc.so");
    long open = lib.getSymbolAddress("open");
    check(open != 0, "libc open not found");
    lib.enumerateFunctions((symbol, addr, size, idx) -> {
      symbols.add(symbol);
      return true;
    });
    check(symbols.contains("open"), "enumerateFunctions miss open");
    symbols.clear();
    lib.enumerateFunctions((symbol, addr, size, idx) -> {
      symbols.add(symbol);
      if ("open".equals(symbol))
        return false;
      return true;
    });
    check("open".equals(symbols.get(symbols.size() - 1)), "enumerateFunctions early stop fail");
    lib.close();

    // FuncHook: hook 前原函数 v+100
    check(getInt(1) == 101, "before hook getInt(1)=" + getInt(1));
    int installed = AlbNative.hookNative(ApiHooker.class, "api");
    check(installed == 2, "hookNative installed=" + installed + " (expect backup+hook)");
    // 替换方法 getInt(1) -> getInt$Backup(101) -> 原函数(101) = 201
    // 该断言同时验证重入 guard: 无 guard 时经 $Backup 调原地址必无限递归栈溢出
    check(getInt(1) == 201, "after hook getInt(1)=" + getInt(1));
    check(ApiHooker.getInt$Backup(5) == 105, "backup direct getInt$Backup(5)=" + ApiHooker.getInt$Backup(5));
    final String[] error = {null};
    Thread worker = new Thread(() -> {
      try {
        int v = getInt(2);
        if (v != 202)
          error[0] = "other thread getInt(2)=" + v;
      } catch (Throwable t) {
        error[0] = "other thread throw:" + t;
      }
    }, "NativeHookTestWorker");
    worker.start();
    try {
      worker.join(10000);
    } catch (InterruptedException e) {
      throw new AssertionError("worker interrupted", e);
    }
    check(error[0] == null, error[0]);

    // unhook: 原函数恢复, 句柄一次性(二次卸载 false), $Backup 不受影响
    check(AlbNative.unHookFunc(ApiHooker.class, "getInt"), "unHookFunc getInt");
    check(getInt(1) == 101, "after unhook getInt(1)=" + getInt(1));
    check(!AlbNative.unHookFunc(ApiHooker.class, "getInt"), "double unHookFunc");
    check(ApiHooker.getInt$Backup(5) == 105, "backup survives unhook");

    int unhookResult = AlbNative.unhookNative(ApiHooker.class, null);
    assert unhookResult == 1 : "unhook:" + unhookResult;
    unhookResult = AlbNative.unhookNative(ApiHooker.class, null);
    assert unhookResult == 0 : "unhook:" + unhookResult;


    // long: 取 0x123456789 使 32 位上的高槽位也参与(错误的高低槽拼装会明显偏离)
    final long A = 0x123456789L;
    check(addLong(A, 7) == A * 1000000L + 7, "before addLong");
    check(AlbNative.hookNative(LongBackupOnly.class, "api") == 1, "install LongBackupOnly");
    long backupOnly = LongBackupOnly.addLong$Backup(A, 7);
    check(backupOnly == A * 1000000L + 7, "backup-only 64bit return=" + backupOnly);
    check(AlbNative.hookNative(LongHooker.class, "api") == 2, "install LongHooker");
    long longGot = addLong(A, 7);
    check(longGot == (A + 1) * 1000000L + 9,
        "after addLong=" + longGot + " backup received:" + getAddLongArg(0) + "," + getAddLongArg(1));

    // String
    check("hi qing".equals(greet("qing")), "before greet");
    check(AlbNative.hookNative(GreetHooker.class, "api") == 2, "install GreetHooker");
    check("hi qing!".equals(greet("qing")), "after greet=" + greet("qing"));

    // 10 参数(arm64 第 9/10 个、x86_64 第 7-10 个走栈)
    check(sum10(1, 2, 3, 4, 5, 6, 7, 8, 9, 10) == 55, "before sum10");
    check(AlbNative.hookNative(SumHooker.class, "api") == 2, "install SumHooker");
    int sumGot = sum10(1, 2, 3, 4, 5, 6, 7, 8, 9, 10);
    StringBuilder sumArgs = new StringBuilder();
    for (int i = 0; i < 10; i++)
      sumArgs.append(getSum10Arg(i)).append(',');
    check(sumGot == 65, "after sum10=" + sumGot + " backup received:" + sumArgs);

    // void
    int counterBefore = getCounter();
    check(AlbNative.hookNative(BumpHooker.class, "api") == 2, "install BumpHooker");
    bump(5);
    check(getCounter() == counterBefore + 10, "void hook counter=" + (getCounter() - counterBefore));

    // 异常兜底
    check(twice(3) == 6, "before twice");
    check(AlbNative.hookNative(ThrowHooker.class, "api") == 2, "install ThrowHooker");
    check(twice(3) == 6, "exception fallback twice(3)=" + twice(3));

    AlbNative.unhookNative(LongBackupOnly.class, null);
    AlbNative.unhookNative(BumpHooker.class);
    AlbNative.unhookNative(ThrowHooker.class);
    AlbNative.unhookNative(LongHooker.class);
    AlbNative.unhookNative(GreetHooker.class);
    AlbNative.unhookNative(SumHooker.class, "api");
  }
}
