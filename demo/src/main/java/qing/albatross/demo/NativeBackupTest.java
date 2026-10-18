package qing.albatross.demo;

import android.content.Context;

import qing.albatross.core.Albatross;
import qing.albatross.nativehook.Address;
import qing.albatross.nativehook.Libc;

/**
 * backup 机制(Java 经备份跳板调用 so 内 C 函数)的分层验证:
 * 层 0 纯 JNI 直调自己的 so(roundTrip, 不经 backup)——验证 so 与文件系统本身;
 * 层 1 backup 纯函数(getpid/abs/strlen)——验证无参/int/String 的参数编组;
 * 层 2 backup 文件 IO 字节级比对(open/write/read/close, byte[]/String/指针三种
 * write 形态)——绕过 NewStringUTF, 直接暴露 read 是否把数据写对 buffer;
 * 层 3 读回字符串(原用例, NewStringUTF 崩溃复现点)。
 */
public class NativeBackupTest {

  static native void writeTest(String path);

  /** 层 0 对照: C 内完成 open/write/read 往返, 返回读回内容, 完全不经 backup。 */
  static native String roundTrip(String path, String content);

  /** 层 0 对照: 普通 JNI int 参数往返。 */
  static native int absDirect(int v);

  /** 诊断对照: 同进程 C 直调 strlen 并打印 dlsym 地址。 */
  static native long strlenDirect(String s);

  static void check(boolean cond, String message) {
    if (!cond)
      throw new AssertionError(message);
  }

  public static void test() {
    if (Albatross.currentApplication() == null) {
      // 原生宿主(albatross_test --java)没有 Application, getDataDir() 不可用
      throw TestRunner.skip("需要 Application 上下文(原生宿主/无 Activity 环境)");
    }
    TestRunner.run("roundTripDirect", NativeBackupTest::testRoundTripDirect);
    TestRunner.run("backupPureFunctions", NativeBackupTest::testBackupPureFunctions);
    TestRunner.run("backupFileIoBytes", NativeBackupTest::testBackupFileIoBytes);
    TestRunner.run("backupFileIoString", NativeBackupTest::testBackupFileIoString);
  }

  /** 层 0: 自己的 so 直接完成文件往返, 结果必须与预期一致。 */
  static void testRoundTripDirect() {
    Context context = Albatross.currentApplication();
    String filePath = context.getDataDir().getAbsoluteFile() + "/native-direct.log";
    String content = "direct round trip\n";
    String back = roundTrip(filePath, content);
    check(content.equals(back), "roundTrip direct: '" + back + "'");
    check(absDirect(-42) == 42 && absDirect(7) == 7, "absDirect");
    Albatross.log("layer0 direct JNI round trip ok");
  }

  /** 层 1: backup 纯函数, 精确断言返回值。 */
  static void testBackupPureFunctions() {
    int pid = LibcPure.getpid();
    check(pid > 0, "backup getpid: " + pid);
    Albatross.log("L1 getpid ok: " + pid);
    long direct = strlenDirect("hello backup");
    check(direct == 12, "strlenDirect: " + direct);
    check(LibcPure.abs(-123) == 123, "backup abs(-123)");
    check(LibcPure.abs(456) == 456, "backup abs(456)");
    check(LibcPure.abs(Integer.MIN_VALUE) == Integer.MIN_VALUE, "backup abs overflow edge");
    Albatross.log("L1 abs ok");
    long len = LibcPure.strlen("hello backup");
    check(len == 12, "backup strlen: " + len);
    Albatross.log("L1 strlen ok: " + len);
    check(LibcPure.strlen("") == 0, "backup strlen empty");
    Albatross.log("layer1 backup pure functions ok");
  }

  /**
   * 层 2: backup 文件 IO, byte[] 写入后以字节级比对读回内容(不经字符串),
   * 同时验证指针参数(write fd,buf,len)与 byte[] 参数两条编组路径。
   */
  static void testBackupFileIoBytes() {
    Context context = Albatross.currentApplication();
    String filePath = context.getDataDir().getAbsoluteFile() + "/native-backup.log";
    byte[] payload = new byte[257];
    for (int i = 0; i < payload.length; i++) {
      payload[i] = (byte) i;
    }
    int fd = Libc.open(filePath, Libc.O_CREAT | Libc.O_TRUNC | Libc.O_WRONLY,
        Integer.parseInt("644", 8));
    check(fd > 0, "backup open: " + fd);
    try {
      // 形态 1: byte[] 参数编组
      long n = Libc.write(fd, payload, payload.length);
      check(n == payload.length, "backup write(byte[]): " + n);
      // 形态 2: 指针参数编组(Address 内存)
      Address address = Address.malloc(64, true);
      try {
        String extra = "pointer write";
        check(address.writeString(extra), "Address.writeString");
        long n2 = Libc.write(fd, address.getAddress(), extra.length());
        check(n2 == extra.length(), "backup write(ptr): " + n2);
      } finally {
        address.delete();
      }
    } finally {
      Libc.close(fd);
    }
    // 读回并逐字节比对
    fd = Libc.open(filePath, Libc.O_RDONLY, 0);
    check(fd > 0, "backup reopen: " + fd);
    try {
      Address buffer = Address.malloc(512, true);
      try {
        long nRead = Libc.read(fd, buffer.getAddress(), buffer.getSize() - 1);
        check(nRead == payload.length + "pointer write".length(),
            "backup read length: " + nRead);
        byte[] readBack = buffer.readBytes((int) nRead);
        check(readBack != null, "readBytes null");
        for (int i = 0; i < payload.length; i++) {
          check(readBack[i] == payload[i],
              "readBack byte " + i + ": " + readBack[i] + " != " + payload[i]);
        }
        String tail = new String(readBack, payload.length, readBack.length - payload.length);
        check("pointer write".equals(tail), "readBack tail: '" + tail + "'");
      } finally {
        buffer.delete();
      }
    } finally {
      Libc.close(fd);
    }
    Albatross.log("layer2 backup file io bytes ok");
  }

  /** 层 3: 字符串写入与读回(含 NewStringUTF 路径, 即原用例崩溃点)。 */
  static void testBackupFileIoString() {
    Context context = Albatross.currentApplication();
    String filePath = context.getDataDir().getAbsoluteFile() + "/native-string.log";
    int fd = Libc.open(filePath, Libc.O_CREAT | Libc.O_TRUNC | Libc.O_WRONLY,
        Integer.parseInt("644", 8));
    check(fd > 0, "open string log: " + fd);
    String content = "string write path\n";
    try {
      long n = Libc.write(fd, content, content.length());
      check(n == content.length(), "backup write(String): " + n);
    } finally {
      Libc.close(fd);
    }
    fd = Libc.open(filePath, Libc.O_RDONLY, 0);
    check(fd > 0, "reopen string log: " + fd);
    try {
      Address buffer = Address.malloc(64, true);
      try {
        long nRead = Libc.read(fd, buffer.getAddress(), buffer.getSize() - 1);
        check(nRead == content.length(), "read string log: " + nRead);
        if (nRead > 0) {
          // 先字节级验证, 再走 NewStringUTF(崩溃复现点)
          byte[] readBack = buffer.readBytes((int) nRead);
          String fromBytes = new String(readBack, 0, (int) nRead);
          check(content.equals(fromBytes), "bytes preview mismatch: '" + fromBytes + "'");
          String s = buffer.readString((int) nRead);
          check(content.equals(s), "readString: '" + s + "'");
        }
      } finally {
        buffer.delete();
      }
    } finally {
      Libc.close(fd);
    }
    Albatross.log("layer3 backup file io string ok");
  }
}
