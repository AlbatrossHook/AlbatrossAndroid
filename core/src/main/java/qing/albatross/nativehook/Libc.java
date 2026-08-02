package qing.albatross.nativehook;


import qing.albatross.annotation.FuncBackup;
import qing.albatross.annotation.Symbol;
import qing.albatross.annotation.TargetLibrary;

@TargetLibrary("c")
public class Libc {
  public static final int O_RDONLY = 0;
  public static final int O_WRONLY = Integer.parseInt("00000001", 8);
  public static final int O_RDWR = Integer.parseInt("00000002", 8);
  public static final int O_CREAT = Integer.parseInt("0100", 8);
  //  public static final int O_EXCL = Integer.parseInt("0200",8);
//  public static final int O_NOCTTY = Integer.parseInt("0400",8);
  public static final int O_TRUNC = Integer.parseInt("01000", 8);
  public static final int O_APPEND = Integer.parseInt("02000", 8);
  public static final int O_NONBLOCK = Integer.parseInt("04000", 8);
  //  public static final int O_DSYNC = Integer.parseInt("010000",8);
  public static final int O_DIRECTORY = Integer.parseInt("0200000", 8);


  @Symbol
  static long open;


  @FuncBackup
  public static native int open(String file, int oflags, int mode);

  @FuncBackup
  public static native long read(int fd, long buf, long nbytes);

  @FuncBackup
  public static native long write(int fd, long buf, long count);

  @FuncBackup
  public static native long write(int fd, String buf, long count);

  @FuncBackup
  public static native long write(int fd, byte[] buf, long count);

  @FuncBackup
  public static native int close(int fd);

  static {
    AlbNative.hookNative();
  }
}
