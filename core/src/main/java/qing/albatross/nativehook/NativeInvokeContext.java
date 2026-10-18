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

public class NativeInvokeContext {

  long mInvokeContext;
  int mFlags;

  NativeInvokeContext(long context, int flags) {
    mInvokeContext = context;
    mFlags = flags;
  }

  public boolean isJavaThread() {
    return (mFlags & 1) == 1;
  }


  public long getNthArgument(int nth) {
    return AlbNative.getNthArgumentNative(mInvokeContext, nth);
  }

  public <T> T getNthArgument(int nth, Class<T> type) {
    long address = AlbNative.getNthArgumentNative(mInvokeContext, nth);
    if (address == 0)
      return null;
    Object obj = AlbNative.readObject(address, type);
    return (T) obj;
  }

  public void setNthArgument(int nth, long value) {
    AlbNative.setNthArgumentNative(mInvokeContext, nth, value);
  }

  public void setResult(long value) {
    AlbNative.setReturnResultNative(mInvokeContext, value);
  }

  public long getResult() {
    return AlbNative.getReturnResultNative(mInvokeContext);
  }

  /**
   * 按寄存器名读取寄存器值（大小写不敏感），名字不被当前架构支持时抛出
   * IllegalArgumentException。支持的名字：
   * arm64: x0-x30、w0-w30（低 32 位）、fp/lr/sp/pc；
   * arm32: r0-r15、fp(=r11)/ip(=r12)/lr/sp/pc；
   * x64: rax/rbx/rcx/rdx/rsi/rdi/rbp/rsp/r8-r15/rip、eax-edi/r8d-r15d（低 32 位）、fp/sp/pc；
   * x86: eax/ebx/ecx/edx/esi/edi/ebp/esp/eip、fp/sp/pc。
   * 注意：onEnter/onLeave 中修改 pc/lr/sp 不会改变控制流或栈指针，仅通用寄存器写真实生效。
   */
  public long getRegister(String name) {
    return AlbNative.getRegValueNative(mInvokeContext, name);
  }

  /**
   * 按寄存器名设置寄存器值，语义同 {@link #getRegister(String)}。
   * 32 位子寄存器名（w0/eax/r8d 等）写入时零扩展到整个寄存器，符合硬件行为。
   */
  public void setRegister(String name, long value) {
    AlbNative.setRegValueNative(mInvokeContext, name, value);
  }

  /** 跨架构读取程序计数器：arm64/arm32 的 pc、x64 的 rip、x86 的 eip。 */
  public long getPC() {
    return getRegister("pc");
  }

  /** 跨架构读取栈指针：arm64/arm32 的 sp、x64 的 rsp、x86 的 esp。 */
  public long getSP() {
    return getRegister("sp");
  }

  /** 跨架构读取帧指针：arm64 的 x29、arm32 的 r11、x64/x86 的 rbp/ebp。 */
  public long getFP() {
    return getRegister("fp");
  }

  /** 读取链接寄存器，仅 ARM 架构支持，x86/x64 上抛出 IllegalArgumentException。 */
  public long getLR() {
    return getRegister("lr");
  }

}
