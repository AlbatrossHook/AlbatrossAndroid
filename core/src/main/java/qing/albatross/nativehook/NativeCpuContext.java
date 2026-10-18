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
 * stalker callout 回调里的 CPU 现场, 只在 {@link StalkerCallout#onCall} 期间有效。
 * 读写寄存器与 {@link NativeInvokeContext} 语义一致:
 * 支持 GPR 全名(x0/ebx...)、统一别名(pc/sp/fp/lr)、32 位子寄存器(w0/eax/r8d,
 * 读低 32 位、写零扩展); pc/lr/sp 槽被框架占用, 写入不生效。
 */
public final class NativeCpuContext {

  private final long cpuContext;

  NativeCpuContext(long cpuContext) {
    this.cpuContext = cpuContext;
  }

  public long getRegister(String reg) {
    return AlbNative.cpuGetRegister(cpuContext, reg);
  }

  public void setRegister(String reg, long value) {
    AlbNative.cpuSetRegister(cpuContext, reg, value);
  }

  /** 按序号读参数（ABI 无关：i386 读栈、x64/arm64 读寄存器；与拦截器
   *  AlbatrossGetNthArgument 同语义）。 */
  public long getArgument(int nth) {
    return AlbNative.cpuGetArgument(cpuContext, nth);
  }

  /** 按序号写参数（语义同 AlbatrossSetNthArgument）。 */
  public void setArgument(int nth, long value) {
    AlbNative.cpuSetArgument(cpuContext, nth, value);
  }

  public long getPC() {
    return getRegister("pc");
  }

  public long getSP() {
    return getRegister("sp");
  }

  public long getFP() {
    return getRegister("fp");
  }

  /** link register, 仅 ARM 架构存在, x86/x64 抛 IllegalArgumentException。 */
  public long getLR() {
    return getRegister("lr");
  }
}
