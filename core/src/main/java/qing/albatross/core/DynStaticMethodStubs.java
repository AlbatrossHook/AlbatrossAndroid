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
 * {@link DynMethodStubs} 的静态方法版：动态 hook 的 backup 槽要求"槽方法的静态性与目标方法一致"
 * （反射 Method 的静态性来自槽方法的声明，而 invoke 时按槽内拷贝的 access_flags 分发，两者不一致
 * 会出现 null receiver / 参数错位）。静态目标用本池，实例目标用 {@link DynMethodStubs}。
 */
public class DynStaticMethodStubs {
  public static native void s0();
  public static native void s1();
  public static native void s2();
  public static native void s3();
  public static native void s4();
  public static native void s5();
  public static native void s6();
  public static native void s7();
  public static native void s8();
  public static native void s9();
  public static native void s10();
  public static native void s11();
  public static native void s12();
  public static native void s13();
  public static native void s14();
  public static native void s15();
  public static native void s16();
  public static native void s17();
  public static native void s18();
  public static native void s19();
  public static native void s20();
  public static native void s21();
  public static native void s22();
  public static native void s23();
  public static native void s24();
  public static native void s25();
  public static native void s26();
  public static native void s27();
  public static native void s28();
  public static native void s29();
  public static native void s30();
  public static native void s31();
  public static native void s32();
  public static native void s33();
  public static native void s34();
  public static native void s35();
  public static native void s36();
  public static native void s37();
  public static native void s38();
  public static native void s39();
  public static native void s40();
  public static native void s41();
  public static native void s42();
  public static native void s43();
  public static native void s44();
  public static native void s45();
  public static native void s46();
  public static native void s47();
  public static native void s48();
  public static native void s49();
  public static native void s50();
  public static native void s51();
  public static native void s52();
  public static native void s53();
  public static native void s54();
  public static native void s55();
  public static native void s56();
  public static native void s57();
  public static native void s58();
  public static native void s59();
  public static native void s60();
  public static native void s61();
  public static native void s62();
  public static native void s63();
}
