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

import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.util.ArrayList;

/**
 * 动态 hook 的调用视图：读改参数/this、读改结果、主动调用原方法。
 * 由 native 侧在每次目标方法被调用时构造，仅在回调线程内有效。
 */
public class CallFrame {
  // 对象池：每次调用的 CallFrame 从线程本地空闲表复用（免每调度一次 Java 分配），
  // 活跃帧按线程栈式管理支持重入（递归/回调内重入各持独立帧）。
  private static final ThreadLocal<ArrayList<CallFrame>> ACTIVE = new ThreadLocal<>();
  private static final ThreadLocal<ArrayList<CallFrame>> POOL = new ThreadLocal<>();
  private static final int POOL_LIMIT = 16;

  MethodCallHook callHook;
  long contextId;
  private Object[] args;
  private Throwable throwable;

  public CallFrame(MethodCallHook callHook, long contextId) {
    reset(callHook, contextId);
  }

  /** 分发开始：取池或新建，压入本线程活跃栈。 */
  static CallFrame begin(MethodCallHook hook, long ctxId) {
    ArrayList<CallFrame> pool = POOL.get();
    CallFrame frame = (pool != null && !pool.isEmpty()) ? pool.remove(pool.size() - 1) : null;
    if (frame == null)
      frame = new CallFrame(null, 0);
    frame.reset(hook, ctxId);
    ArrayList<CallFrame> active = ACTIVE.get();
    if (active == null) {
      active = new ArrayList<>(4);
      ACTIVE.set(active);
    }
    active.add(frame);
    return frame;
  }

  /** 当前线程最内层的活跃帧（invokeOriginal / afterCall 用）。 */
  static CallFrame current() {
    ArrayList<CallFrame> active = ACTIVE.get();
    if (active == null || active.isEmpty())
      throw new IllegalStateException("no active CallFrame on this thread");
    return active.get(active.size() - 1);
  }

  /** 分发结束：弹栈、清理引用并归还池。 */
  static void end() {
    ArrayList<CallFrame> active = ACTIVE.get();
    if (active == null || active.isEmpty())
      throw new IllegalStateException("CallFrame stack underflow");
    CallFrame frame = active.remove(active.size() - 1);
    frame.reset(null, 0);
    ArrayList<CallFrame> pool = POOL.get();
    if (pool == null) {
      pool = new ArrayList<>(4);
      POOL.set(pool);
    }
    if (pool.size() < POOL_LIMIT)
      pool.add(frame);
  }

  private void reset(MethodCallHook hook, long ctxId) {
    callHook = hook;
    contextId = ctxId;
    args = null;
    throwable = null;
  }

  public Member getMember() {
    return callHook.member;
  }

  public Method getMethod() {
    return (Method) callHook.member;
  }

  public Object getThis() {
    if (callHook.argOffset == 0)
      return null;
    return MethodCallHook.dynGetParamObject(contextId, 0);
  }

  public void setThis(Object self) {
    if (callHook.argOffset != 1)
      throw new IllegalArgumentException("static method has no this: " + callHook.member);
    MethodCallHook.dynSetParamObject(contextId, 0, self);
  }

  public int getParamCount() {
    return callHook.parameterTypes.length;
  }

  /**
   * 装箱后的参数（不含 this）。数组可修改：修改会在
   * {@link #invokeOriginal()} 与自动执行原方法时生效（与 typed setParamXxx 等价）。
   */
  public Object[] getArguments() {
    if (args == null) {
      Class<?>[] types = callHook.parameterTypes;
      Object[] a = new Object[types.length];
      for (int i = 0; i < types.length; i++) {
        Class<?> t = types[i];
        int slot = callHook.argOffset + i;
        Object v;
        if (t == boolean.class) {
          v = MethodCallHook.dynGetParamInt(contextId, slot) != 0;
        } else if (t == byte.class) {
          v = (byte) MethodCallHook.dynGetParamInt(contextId, slot);
        } else if (t == char.class) {
          v = (char) MethodCallHook.dynGetParamInt(contextId, slot);
        } else if (t == short.class) {
          v = (short) MethodCallHook.dynGetParamInt(contextId, slot);
        } else if (t == int.class) {
          v = MethodCallHook.dynGetParamInt(contextId, slot);
        } else if (t == long.class) {
          v = MethodCallHook.dynGetParamLong(contextId, slot);
        } else if (t == float.class) {
          v = MethodCallHook.dynGetParamFloat(contextId, slot);
        } else if (t == double.class) {
          v = MethodCallHook.dynGetParamDouble(contextId, slot);
        } else {
          v = MethodCallHook.dynGetParamObject(contextId, slot);
        }
        a[i] = v;
      }
      args = a;
    }
    return args;
  }

  private int slot(int i) {
    if (i < 0 || i >= callHook.parameterTypes.length)
      throw new IndexOutOfBoundsException("param " + i + " of " + callHook.member);
    return callHook.argOffset + i;
  }

  private void checkType(int i, Class<?> t) {
    if (callHook.parameterTypes[i] != t)
      throw new IllegalArgumentException("param " + i + " of " + callHook.member + " is " + callHook.parameterTypes[i]);
  }

  public boolean getParamBoolean(int i) {
    checkType(i, boolean.class);
    return MethodCallHook.dynGetParamInt(contextId, slot(i)) != 0;
  }

  public byte getParamByte(int i) {
    checkType(i, byte.class);
    return (byte) MethodCallHook.dynGetParamInt(contextId, slot(i));
  }

  public char getParamChar(int i) {
    checkType(i, char.class);
    return (char) MethodCallHook.dynGetParamInt(contextId, slot(i));
  }

  public short getParamShort(int i) {
    checkType(i, short.class);
    return (short) MethodCallHook.dynGetParamInt(contextId, slot(i));
  }

  public int getParamInt(int i) {
    checkType(i, int.class);
    return MethodCallHook.dynGetParamInt(contextId, slot(i));
  }

  public long getParamLong(int i) {
    checkType(i, long.class);
    return MethodCallHook.dynGetParamLong(contextId, slot(i));
  }

  public float getParamFloat(int i) {
    checkType(i, float.class);
    return MethodCallHook.dynGetParamFloat(contextId, slot(i));
  }

  public double getParamDouble(int i) {
    checkType(i, double.class);
    return MethodCallHook.dynGetParamDouble(contextId, slot(i));
  }

  /** 该目标是否走引用桥（安全路径）；false = legacy 路径，引用参数带已知 GC 窗口。 */
  public boolean usesRefBridge() {
    return MethodCallHook.dynUsesRefBridge(contextId);
  }

  public Object getParamObject(int i) {
    if (callHook.parameterTypes[i].isPrimitive())
      throw new IllegalArgumentException("param " + i + " of " + callHook.member + " is primitive");
    return MethodCallHook.dynGetParamObject(contextId, slot(i));
  }

  public void setParamBoolean(int i, boolean v) {
    checkType(i, boolean.class);
    MethodCallHook.dynSetParamInt(contextId, slot(i), v ? 1 : 0);
    if (args != null) args[i] = v;
  }

  public void setParamByte(int i, byte v) {
    checkType(i, byte.class);
    MethodCallHook.dynSetParamInt(contextId, slot(i), v);
    if (args != null) args[i] = v;
  }

  public void setParamChar(int i, char v) {
    checkType(i, char.class);
    MethodCallHook.dynSetParamInt(contextId, slot(i), v);
  }

  public void setParamShort(int i, short v) {
    checkType(i, short.class);
    MethodCallHook.dynSetParamInt(contextId, slot(i), v);
  }

  public void setParamInt(int i, int v) {
    checkType(i, int.class);
    MethodCallHook.dynSetParamInt(contextId, slot(i), v);
    if (args != null) args[i] = v;
  }

  public void setParamLong(int i, long v) {
    checkType(i, long.class);
    MethodCallHook.dynSetParamLong(contextId, slot(i), v);
    if (args != null) args[i] = v;
  }

  public void setParamFloat(int i, float v) {
    checkType(i, float.class);
    MethodCallHook.dynSetParamFloat(contextId, slot(i), v);
    if (args != null) args[i] = v;
  }

  public void setParamDouble(int i, double v) {
    checkType(i, double.class);
    MethodCallHook.dynSetParamDouble(contextId, slot(i), v);
    if (args != null) args[i] = v;
  }

  public void setParamObject(int i, Object v) {
    if (callHook.parameterTypes[i].isPrimitive())
      throw new IllegalArgumentException("param " + i + " of " + callHook.member + " is primitive");
    MethodCallHook.dynSetParamObject(contextId, slot(i), v);
    if (args != null) args[i] = v;
  }

  /** 主动调用原方法（不经过 hook），使用当前参数视图（含修改）。 */
  public Object invokeOriginal() {
    return callHook.invokeOriginalMethod(contextId);
  }

  public boolean invokeBoolean() {
    return (Boolean) invokeOriginal();
  }

  public char invokeChar() {
    return (Character) invokeOriginal();
  }

  public byte invokeByte() {
    return (Byte) invokeOriginal();
  }

  public short invokeShort() {
    return (Short) invokeOriginal();
  }

  public int invokeInt() {
    Number n = (Number) invokeOriginal();
    return n == null ? 0 : n.intValue();
  }

  public long invokeLong() {
    Number n = (Number) invokeOriginal();
    return n == null ? 0 : n.longValue();
  }

  public float invokeFloat() {
    Number n = (Number) invokeOriginal();
    return n == null ? 0 : n.floatValue();
  }

  public double invokeDouble() {
    Number n = (Number) invokeOriginal();
    return n == null ? 0 : n.doubleValue();
  }

  public Object invokeObject() {
    return invokeOriginal();
  }

  /**
   * 当前结果（afterCall 中读取；跳过原方法且未 setResult 时为 null/默认值）。
   */
  public Object getResult() {
    switch (callHook.returnType) {
      case VOID:
        return null;
      case BOOL:
        return MethodCallHook.dynGetRawI(contextId) != 0;
      case CHAR:
        return (char) MethodCallHook.dynGetRawI(contextId);
      case BYTE:
        return (byte) MethodCallHook.dynGetRawI(contextId);
      case SHORT:
        return (short) MethodCallHook.dynGetRawI(contextId);
      case INT:
        return MethodCallHook.dynGetRawI(contextId);
      case LONG:
        return MethodCallHook.dynGetRawJ(contextId);
      case FLOAT:
        return MethodCallHook.dynGetRawF(contextId);
      case DOUBLE:
        return MethodCallHook.dynGetRawD(contextId);
      default:
        return MethodCallHook.dynGetRawObject(contextId);
    }
  }

  /** 设置返回值；在 beforeCall 中调用即隐含"跳过原方法"。 */
  public void setResult(Object value) {
    switch (callHook.returnType) {
      case VOID:
        return;
      case BOOL:
        MethodCallHook.dynSetResultI(contextId, ((Boolean) value) ? 1 : 0);
        break;
      case CHAR:
        MethodCallHook.dynSetResultI(contextId, (Character) value);
        break;
      case BYTE:
      case SHORT:
      case INT:
        MethodCallHook.dynSetResultI(contextId, ((Number) value).intValue());
        break;
      case LONG:
        MethodCallHook.dynSetResultJ(contextId, ((Number) value).longValue());
        break;
      case FLOAT:
        MethodCallHook.dynSetResultF(contextId, ((Number) value).floatValue());
        break;
      case DOUBLE:
        MethodCallHook.dynSetResultD(contextId, ((Number) value).doubleValue());
        break;
      default:
        MethodCallHook.dynSetResultObject(contextId, value);
        break;
    }
  }

  /** 当前待传播异常（原方法抛出的已解包，beforeCall/afterCall 设置的为准）。 */
  public Throwable getThrowable() {
    return throwable;
  }

  /** 设置待传播异常；在 beforeCall 中调用即隐含"跳过原方法"。 */
  public void setThrowable(Throwable t) {
    this.throwable = t;
    MethodCallHook.dynSetThrowable(contextId, t);
  }
}
