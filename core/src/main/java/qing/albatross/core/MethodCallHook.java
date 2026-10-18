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

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 一次 {@link Albatross#hookMethod(Member, MethodHook)} 的句柄，
 * 同时充当 native 分发的宿主（invokeOriginalMethod 由 C bridge 调用）。
 */
public class MethodCallHook {

  Member member;
  long listenerId = 0;
  int argOffset;
  ReturnType returnType;
  final Class<?>[] parameterTypes;
  private Method originalMethod;
  // 同一方法的多个回调在此聚合：kinds/返回类型/克隆备份只解析一份，
  // native 仅安装一个 trampoline。before 正序、after 逆序（pine/Xposed 语义）。
  final List<MethodHookCallback> hooks = new CopyOnWriteArrayList<>();

  /**
   * 卸载全部回调并摘除 trampoline（句柄失效，不可再 addHook）。
   */
  public synchronized void unHook() {
    if (listenerId != 0) {
      hooks.clear();
      Albatross.transactionBegin();
      Albatross.unHookMethodNative(listenerId);
      Albatross.transactionEnd(true);
      listenerId = 0;
      // 原方法反射句柄随 record 一起失效（native 侧 record 已回收/detach），缓存必须作废
      originalMethod = null;
      Albatross.onDynHookRemoved(this);
    }
  }

  /**
   * 仅卸载单个回调；链空时自动摘除 trampoline。
   *
   * @return 回调原本存在且已移除
   */
  public synchronized boolean unHook(MethodHookCallback callback) {
    if (!hooks.remove(callback))
      return false;
    if (hooks.isEmpty() && listenerId != 0) {
      unHook();
    }
    return true;
  }

  /** 追加回调（须在 hookMethod 返回的句柄上进行；已 unHook 的句柄抛出）。 */
  public synchronized void addHook(MethodHookCallback callback) {
    if (listenerId == 0) {
      throw new IllegalStateException("hook handle is dead: " + member);
    }
    if (callback != null && !hooks.contains(callback)) {
      hooks.add(callback);
    }
  }

  /** native 分发入口（参数为 ctxId）：从线程本地池取 CallFrame，before 正序聚合，
   * 任一返回 false 即跳过原方法；回调抛异常捕获后置入 ctx 待传播（跳过原方法）。 */
  boolean dispatchBefore(long ctxId) {
    CallFrame frame = CallFrame.begin(this, ctxId);
    boolean proceed = true;
    try {
      for (MethodHookCallback cb : hooks) {
        if (!cb.beforeCall(frame))
          proceed = false;
      }
    } catch (Throwable t) {
      frame.setThrowable(t); // 同步帧字段（afterCall 的 getThrowable）与 ctx
    }
    return proceed;
  }

  /** native 分发入口：after 逆序；finally 归还 CallFrame 到池。 */
  void dispatchAfter(long ctxId) {
    try {
      CallFrame frame = CallFrame.current();
      for (int i = hooks.size() - 1; i >= 0; i--) {
        hooks.get(i).afterCall(frame);
      }
    } catch (Throwable t) {
      CallFrame.current().setThrowable(t); // afterCall 异常替换待传播异常
    } finally {
      CallFrame.end();
    }
  }

  MethodCallHook(Member member) {
    if (member instanceof Constructor<?>) {
      throw new IllegalArgumentException("dynamic hook does not support constructors yet: " + member);
    }
    this.member = member;
    Method method = (Method) member;
    parameterTypes = method.getParameterTypes();
    argOffset = Modifier.isStatic(method.getModifiers()) ? 0 : 1;
    Class<?> retClass = method.getReturnType();
    returnType = classReturnType(retClass);
    if (Modifier.isAbstract(method.getModifiers())) {
      throw new IllegalArgumentException("cannot hook abstract method: " + member);
    }
  }

  static ReturnType classReturnType(Class<?> retClass) {
    if (retClass == void.class || retClass == Void.class) return ReturnType.VOID;
    if (retClass == boolean.class || retClass == Boolean.class) return ReturnType.BOOL;
    if (retClass == char.class || retClass == Character.class) return ReturnType.CHAR;
    if (retClass == byte.class || retClass == Byte.class) return ReturnType.BYTE;
    if (retClass == short.class || retClass == Short.class) return ReturnType.SHORT;
    if (retClass == int.class || retClass == Integer.class) return ReturnType.INT;
    if (retClass == float.class || retClass == Float.class) return ReturnType.FLOAT;
    if (retClass == long.class || retClass == Long.class) return ReturnType.LONG;
    if (retClass == double.class || retClass == Double.class) return ReturnType.DOUBLE;
    return ReturnType.OBJECT;
  }

  /** 参数种类码（与 native DynArgKind 一致，0=引用）。 */
  static byte kindOf(Class<?> c) {
    if (c == boolean.class) return 1;
    if (c == byte.class) return 2;
    if (c == char.class) return 3;
    if (c == short.class) return 4;
    if (c == int.class) return 5;
    if (c == long.class) return 6;
    if (c == float.class) return 7;
    if (c == double.class) return 8;
    return 0;
  }

  /**
   * native bridge 调用：反射**真实方法**执行原实现，返回装箱结果（void 返回 null）。
   * 原方法抛出的异常解包后放入 frame.throwable。
   *
   * 反射的是真实方法（不再是堆克隆的伪造 jmethodID）：debuggable 进程里 ART 的 JNI id 是
   * 索引模式，伪造的克隆 id 一旦被编码（FromReflectedMethod / 调试器枚举 / 第三方反射展开）
   * 就越界崩或静默毁堆，见 doc/pitfalls/pitfall-dynhook-clone-method-id-20261007.md。
   * 反射会再次进入被 hook 方法的 trampoline —— dynPrepareInvoke 在反射前武装"直通槽"，
   * trampoline 命中后直接尾跳到原 entry，不再重入 hook（嵌套的递归调用则照常进 hook）。
   *
   * 为什么用反射而不是 native 直调 ArtMethod::Invoke（2026-10-04 结论，见 dynamic_hook.cpp 顶部）：
   * ArtMethod::Invoke 要求调用线程处于 kRunnable（持有 mutator lock），ART 自己的反射路径用
   * ScopedObjectAccess 完成这个转场；从 JNI native 直调则跳过它、线程停在 kNative —— 此时 GC
   * 既不等这个线程、也不会走它的解释器影子帧，被调方法里的参数/返回值在对象搬家后全成悬垂引用
   * （DynamicHookStressTest 的 gcHammer60s 每次崩在 native 返回值校验处，klass 读到 0）。
   * 反射路径没有这个窗口：参数与结果由 ART 自己在持锁状态下转换。
   */
  Object invokeOriginalMethod(long ctxId) {
    // 注意：不能用 listenerId==0 当"死句柄"门禁——unHook 与在途 dispatch 天然并发，
    // 句柄被卸载时 record 仍可能持有 active 计数（槽也没归还），此时反射原方法是合法的。
    // 真正拿已释放 record 的调用在 ctx 层面就是未定义行为（native 侧 record 释放有 quiesce 窗口）。
    try {
      Method m = getOriginalMethod(ctxId);
      CallFrame frame = CallFrame.current();
      // 顺序很关键：参数数组构造/装箱都会分配（= 安全点），而直通槽的武装必须是反射调用前的
      // 最后一件事（dynPrepareInvoke 里做），反射一进入 trampoline 就得能匹配到。
      Object self = frame.getThis();
      Object[] args = frame.getArguments();
      dynPrepareInvoke(ctxId);
      try {
        // 有返回值/异常的路径都要解除武装：正常路径（trampoline 直通命中）键已被消费，
        // 这里兜住"反射没进我们的 trampoline"的漂移（接收方类覆盖了目标方法，Java 反射
        // 按接收方动态分发）—— 否则残留的键会吞掉本线程下一次调用的 hook。
        return m.invoke(self, args);
      } finally {
        dynDisarmPassthrough(ctxId);
      }
    } catch (InvocationTargetException e) {
      // 解包原始异常写入当前帧（afterCall 的 getThrowable）与 ctx
      CallFrame.current().setThrowable(e.getCause());
      return null;
    } catch (IllegalAccessException e) {
      throw new RuntimeException(e);
    }
  }

  private synchronized Method getOriginalMethod(long ctxId) {
    if (originalMethod == null) {
      originalMethod = dynGetOriginalMethod(ctxId);
      if (originalMethod == null) {
        throw new IllegalStateException("failed to create original method for " + member);
      }
      originalMethod.setAccessible(true);
    }
    return originalMethod;
  }

  static native int dynGetParamInt(long contextId, int idx);

  static native long dynGetParamLong(long contextId, int idx);

  static native float dynGetParamFloat(long contextId, int idx);

  static native double dynGetParamDouble(long contextId, int idx);

  static native Object dynGetParamObject(long contextId, int idx);

  static native void dynSetParamInt(long contextId, int idx, int v);

  static native void dynSetParamLong(long contextId, int idx, long v);

  static native void dynSetParamFloat(long contextId, int idx, float v);

  static native void dynSetParamDouble(long contextId, int idx, double v);

  static native void dynSetParamObject(long contextId, int idx, Object v);

  static native void dynSetResultI(long contextId, int v);

  static native void dynSetResultJ(long contextId, long v);

  static native void dynSetResultF(long contextId, float v);

  static native void dynSetResultD(long contextId, double v);

  static native void dynSetResultObject(long contextId, Object v);

  static native int dynGetRawI(long contextId);

  static native long dynGetRawJ(long contextId);

  static native float dynGetRawF(long contextId);

  static native double dynGetRawD(long contextId);

  static native Object dynGetRawObject(long contextId);

  static native void dynSetThrowable(long contextId, Throwable t);

  static native void dynPrepareInvoke(long contextId);

  /** 反射调用返回后解除直通槽武装（invokeOriginalMethod 的 finally 调用，防漂移残留）。 */
  static native void dynDisarmPassthrough(long contextId);

  /**
   * 该目标的引用参数是否走"引用桥"（寄存器引用经桥形参交给 ART 托管、栈引用现读）。
   * false 表示槽位不足、退回 legacy 路径 —— 那条路径的引用转换跨了一次 JNI 过渡，
   * 若此刻有挂起中的 GC，捕获的裸值会作废（x86_64 已实测可复现崩）。仅回调期可查。
   */
  static native boolean dynUsesRefBridge(long contextId);

  static native Method dynGetOriginalMethod(long contextId);

  // Java 桥方法：trampoline 保存参数现场到堆后尾跳转到其 entry（lr 原封不动，
  // 对栈遍历完全透明），按 hook 目标方法的返回类型选用其一；
  // 三个 long 参数为 (record, extras, orig_sp)。
  static void jBridgeV(long r, long e, long sp) {
    nDispatchV(r, e, sp);
  }

  static long jBridgeJ(long r, long e, long sp) {
    return nDispatchJ(r, e, sp);
  }

  static float jBridgeF(long r, long e, long sp) {
    return nDispatchF(r, e, sp);
  }

  static double jBridgeD(long r, long e, long sp) {
    return nDispatchD(r, e, sp);
  }

  static Object jBridgeL(long r, long e, long sp) {
    return nDispatchL(r, e, sp);
  }

  // 32 位（arm32/x86）桥重载：三个 int 参数恰好占 arm32 的 r1-r3、x86 的 ecx/edx/ebx，
  // 内部零成本拓宽到 long native。返回值必须与目标方法返回类型一致（尾跳后调用方按
  // 原类型取值），所以 long 与 int 族分成两个方法（参数列表相同，只能靠方法名区分）。
  static void jBridgeV32(int r, int e, int sp) {
    nDispatchV(r, e, sp);
  }

  static int jBridgeI32(int r, int e, int sp) {
    return (int) nDispatchJ(r, e, sp);
  }

  static long jBridgeJ32(int r, int e, int sp) {
    return nDispatchJ(r, e, sp);
  }

  static float jBridgeF32(int r, int e, int sp) {
    return nDispatchF(r, e, sp);
  }

  static double jBridgeD32(int r, int e, int sp) {
    return nDispatchD(r, e, sp);
  }

  static Object jBridgeO32(int r, int e, int sp) {
    return nDispatchL(r, e, sp);
  }

  // refBridge 路径的桥：第一个形参是本次调用的 ctx，后 4 个 Object 是"寄存器里的引用参数"
  //（参数更少的位置传 null）。引用由 ART 在桥的帧里登记成托管引用，GC 自动维护 —— native
  // 侧不再需要任何"跨检查点保持裸指针"的技巧，只需把它们转成全局引用。
  static void jBridgeRefsV(long c, Object o0, Object o1, Object o2, Object o3) {
    nDispatchRefsV(c, o0, o1, o2, o3);
  }

  static long jBridgeRefsJ(long c, Object o0, Object o1, Object o2, Object o3) {
    return nDispatchRefsJ(c, o0, o1, o2, o3);
  }

  static float jBridgeRefsF(long c, Object o0, Object o1, Object o2, Object o3) {
    return nDispatchRefsF(c, o0, o1, o2, o3);
  }

  static double jBridgeRefsD(long c, Object o0, Object o1, Object o2, Object o3) {
    return nDispatchRefsD(c, o0, o1, o2, o3);
  }

  static Object jBridgeRefsL(long c, Object o0, Object o1, Object o2, Object o3) {
    return nDispatchRefsL(c, o0, o1, o2, o3);
  }

  static native void nDispatchRefsV(long c, Object o0, Object o1, Object o2, Object o3);

  static native long nDispatchRefsJ(long c, Object o0, Object o1, Object o2, Object o3);

  static native float nDispatchRefsF(long c, Object o0, Object o1, Object o2, Object o3);

  static native double nDispatchRefsD(long c, Object o0, Object o1, Object o2, Object o3);

  static native Object nDispatchRefsL(long c, Object o0, Object o1, Object o2, Object o3);

  // 捕获点根基化路径的桥：引用句柄都在 native ctx 里（trampoline helper 已 NewLocalRef 根基化，
  // GC 自动维护），桥只带一个 ctx —— 所以栈引用、超过寄存器槽位的引用也走安全路径。
  static void jBridgeRootV(long c) {
    nDispatchRootV(c);
  }

  static long jBridgeRootJ(long c) {
    return nDispatchRootJ(c);
  }

  static float jBridgeRootF(long c) {
    return nDispatchRootF(c);
  }

  static double jBridgeRootD(long c) {
    return nDispatchRootD(c);
  }

  static Object jBridgeRootL(long c) {
    return nDispatchRootL(c);
  }

  static native void nDispatchRootV(long c);

  static native long nDispatchRootJ(long c);

  static native float nDispatchRootF(long c);

  static native double nDispatchRootD(long c);

  static native Object nDispatchRootL(long c);

  // 32 位版根基化桥：int 参数（managed ABI 对 long 形参有寄存器对要求），经隐式加宽进 native。
  static void jBridgeRoot32V(int c) {
    nDispatchRootV(c);
  }

  static int jBridgeRoot32I(int c) {
    return (int) nDispatchRootJ(c);
  }

  static long jBridgeRoot32J(int c) {
    return nDispatchRootJ(c);
  }

  static float jBridgeRoot32F(int c) {
    return nDispatchRootF(c);
  }

  static double jBridgeRoot32D(int c) {
    return nDispatchRootD(c);
  }

  static Object jBridgeRoot32O(int c) {
    return nDispatchRootL(c);
  }

  static native void nDispatchV(long r, long e, long sp);

  static native long nDispatchJ(long r, long e, long sp);

  static native float nDispatchF(long r, long e, long sp);

  static native double nDispatchD(long r, long e, long sp);

  static native Object nDispatchL(long r, long e, long sp);
}
