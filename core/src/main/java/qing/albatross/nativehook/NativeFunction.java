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

/**
 * 任意 native 地址的带签名调用(对齐 frida NativeFunction), 不限于 CModule。
 *
 * <pre>{@code
 * NativeFunction f = new NativeFunction(address, Const.ARG_INT, Const.ARG_INT, Const.ARG_STR);
 * Object ret = f.call(1, 2, "abc");
 * }</pre>
 *
 * <p>参数类型用 {@code Const.ARG_*} 描述, Java 参数按类型映射:
 * Integer/Boolean→整型槽, Long→64 位(32 位平台自动拆槽),
 * String→UTF-8 指针(仅调用期间有效), byte[]→数组首址(调用后失效),
 * Float/Double→浮点槽。
 *
 * <p>返回按 retType 装箱: INT/LONG/PTR→Long, BOOL→Boolean, FLOAT→Float,
 * DOUBLE→Double, STR→String(复制返回的 char*), VOID→null。
 * 指针/字符串参数允许传 null(即 NULL)。
 *
 * <p>被 Albatross hook 的地址调用时经过替换蹦床, 语义同 frida。
 */
public final class NativeFunction {

  private final long address;
  private final byte retType;
  private final byte[] argTypes;

  public NativeFunction(long address, byte retType, byte... argTypes) {
    if (address == 0)
      throw new IllegalArgumentException("null function address");
    this.address = address;
    this.retType = retType;
    this.argTypes = argTypes == null ? new byte[0] : argTypes.clone();
  }

  public long getAddress() {
    return address;
  }

  /**
   * 按声明的参数个数与类型调用; 个数不匹配抛 IllegalArgumentException,
   * 不支持的签名抛 UnsupportedOperationException。
   */
  public Object call(Object... args) {
    Object[] actual = args == null ? new Object[0] : args;
    if (actual.length != argTypes.length)
      throw new IllegalArgumentException(
          "expected " + argTypes.length + " args, got " + actual.length);
    return AlbNative.dynamicCallNative(address, retType, argTypes, actual);
  }
}
