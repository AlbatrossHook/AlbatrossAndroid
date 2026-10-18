/*
 * Copyright 2025 QingWan (qingwanmail@foxmail.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package qing.albatross.nativehook;


import qing.albatross.annotation.FuncBackup;
import qing.albatross.annotation.Symbol;
import qing.albatross.annotation.TargetLibrary;
import qing.albatross.annotation.Word64;

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
  // 32 位上 ssize_t/size_t/指针都是单机器字, 不标 @Word64 会按 64 位编组错位
  @Word64
  public static native long read(int fd, @Word64 long buf, @Word64 long nbytes);

  @FuncBackup
  @Word64
  public static native long write(int fd, @Word64 long buf, @Word64 long count);

  @FuncBackup
  @Word64
  public static native long write(int fd, String buf, @Word64 long count);

  @FuncBackup
  @Word64
  public static native long write(int fd, byte[] buf, @Word64 long count);

  @FuncBackup
  public static native int close(int fd);

  static {
    AlbNative.hookNative();
  }
}
