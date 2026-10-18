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
package qing.albatross.core;

/**
 * 动态 hook（MethodCallHook/invokeOriginal）专用 stub 方法槽池。
 *
 * <p>每个槽是一个 public native 方法（public 才能被 JNI GetMethodID 直接取到 index id mid；，native 侧用 Jit::AcquireStubSlot 把它整体改写成
 * 被 hook 方法的一份副本当 backup 用：槽位于真实类的方法数组内，GC 会随类搬迁维护其中的
 * declaring_class 等引用，因此不像堆克隆那样需要 GC 钩子回填。
 *
 * <p>槽由 native 侧在 hook 安装时借出、卸载时归还（ReleaseStubMethod 把原内容逐字节回滚），
 * 池尽时自动退化为堆克隆 + GC 钩子回填。方法与 {@link MethodStubs} 隔离，避免与注解式
 * backupCall 争用同一池子。
 */
public class DynMethodStubs {
  public native void d0();

  public native void d1();

  public native void d2();

  public native void d3();

  public native void d4();

  public native void d5();

  public native void d6();

  public native void d7();

  public native void d8();

  public native void d9();

  public native void d10();

  public native void d11();

  public native void d12();

  public native void d13();

  public native void d14();

  public native void d15();

  public native void d16();

  public native void d17();

  public native void d18();

  public native void d19();

  public native void d20();

  public native void d21();

  public native void d22();

  public native void d23();

  public native void d24();

  public native void d25();

  public native void d26();

  public native void d27();

  public native void d28();

  public native void d29();

  public native void d30();

  public native void d31();

  public native void d32();

  public native void d33();

  public native void d34();

  public native void d35();

  public native void d36();

  public native void d37();

  public native void d38();

  public native void d39();

  public native void d40();

  public native void d41();

  public native void d42();

  public native void d43();

  public native void d44();

  public native void d45();

  public native void d46();

  public native void d47();

  public native void d48();

  public native void d49();

  public native void d50();

  public native void d51();

  public native void d52();

  public native void d53();

  public native void d54();

  public native void d55();

  public native void d56();

  public native void d57();

  public native void d58();

  public native void d59();

  public native void d60();

  public native void d61();

  public native void d62();

  public native void d63();
}
