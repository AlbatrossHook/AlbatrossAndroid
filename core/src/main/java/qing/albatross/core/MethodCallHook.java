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

import android.util.ArrayMap;

import java.lang.reflect.Constructor;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Map;

import qing.albatross.annotation.Alias;
import qing.albatross.annotation.ByName;
import qing.albatross.reflection.MethodDef;
import qing.albatross.reflection.VoidMethodDef;


public class MethodCallHook {


  static Map<Class<?>, ReturnType> classReturnTypeMap;


  static {
    classReturnTypeMap = new ArrayMap<>(10);
    classReturnTypeMap.put(void.class, ReturnType.VOID);
    classReturnTypeMap.put(Void.class, ReturnType.VOID);
    classReturnTypeMap.put(boolean.class, ReturnType.BOOL);
    classReturnTypeMap.put(char.class, ReturnType.CHAR);
    classReturnTypeMap.put(byte.class, ReturnType.BYTE);
    classReturnTypeMap.put(short.class, ReturnType.SHORT);
    classReturnTypeMap.put(int.class, ReturnType.INT);
    classReturnTypeMap.put(float.class, ReturnType.FLOAT);
    classReturnTypeMap.put(long.class, ReturnType.LONG);
    classReturnTypeMap.put(double.class, ReturnType.DOUBLE);
  }


  Member member;
  long listenerId = 0;
  int argOffset;
  ReturnType returnType;
  MethodCallback callback;
  Class<?>[] parameterTypes;

  public synchronized void unHook() {
    if (listenerId != 0) {
      Albatross.unHookMethodNative(listenerId);
      listenerId = 0;
    }
  }

  public MethodCallHook(Member member) {
    this.member = member;
    if (member instanceof Constructor<?>) {
      parameterTypes = ((Constructor<?>) member).getParameterTypes();
      argOffset = 1;
      returnType = ReturnType.VOID;
    } else {
      Method method = (Method) member;
      parameterTypes = method.getParameterTypes();
      if (Modifier.isStatic(member.getModifiers()))
        argOffset = 0;
      else
        argOffset = 1;
      Class<?> retClass = method.getReturnType();
      returnType = classReturnTypeMap.get(retClass);
      if (returnType == null) {
        returnType = ReturnType.OBJECT;
      }
    }
  }

  @Alias("callVoid")
  private void callVoid(long invocationContext) {
    callback.call(new CallFrame(this, invocationContext));
  }


  static class Image {
    @ByName("callVoid")
    public static VoidMethodDef callVoid;
    @ByName("callBool")
    public static MethodDef<Boolean> callBool;
    @ByName("callChar")
    public static MethodDef<Character> callChar;
    @ByName("callByte")
    public static MethodDef<Byte> callByte;
    @ByName("callShort")
    public static MethodDef<Short> callShort;
    @ByName("callInt")
    public static MethodDef<Integer> callInt;
    @ByName("callFloat")
    public static MethodDef<Float> callFloat;
    @ByName("callLong")
    public static MethodDef<Long> callLong;
    @ByName("callDouble")
    public static MethodDef<Double> callDouble;
    @ByName("callObject")
    public static MethodDef<Object> callObject;
  }


  int getParamCount() {
    return parameterTypes.length;
  }

  @Alias("callBool")
  private boolean callBool(long invocationContext) {
    return (boolean) callback.call(new CallFrame(this, invocationContext));
  }


  @Alias("callChar")
  private char callChar(long invocationContext) {
    return (char) callback.call(new CallFrame(this, invocationContext));
  }


  @Alias("callByte")
  private byte callByte(long invocationContext) {
    return (byte) callback.call(new CallFrame(this, invocationContext));
  }


  @Alias("callShort")
  private short callShort(long invocationContext) {
    return (short) callback.call(new CallFrame(this, invocationContext));
  }


  @Alias("callInt")
  private int callInt(long invocationContext) {
    return (int) callback.call(new CallFrame(this, invocationContext));
  }


  @Alias("callFloat")
  private float callFloat(long invocationContext) {
    return (float) callback.call(new CallFrame(this, invocationContext));
  }


  @Alias("callLong")
  private long callLong(long invocationContext) {
    return (long) callback.call(new CallFrame(this, invocationContext));
  }


  @Alias("callDouble")
  private double callDouble(long invocationContext) {
    return (double) callback.call(new CallFrame(this, invocationContext));
  }


  @Alias("callObject")
  private Object callObject(long invocationContext) {
    return callback.call(new CallFrame(this, invocationContext));
  }


  static native boolean invokeBool(long invocationContext);


  static native char invokeChar(long invocationContext);


  static native byte invokeByte(long invocationContext);


  static native short invokeShort(long invocationContext);


  static native int invokeInt(long invocationContext);


  static native float invokeFloat(long invocationContext);


  static native long invokeLong(long invocationContext);


  static native double invokeDouble(long invocationContext);


  static native Object invokeObject(long invocationContext);


//  static native boolean getParamBool(long invocationContext, int idx);
//
//  static native char getParamChar(long invocationContext, int idx);
//
//  static native byte getParamByte(long invocationContext, int idx);
//
//  static native short getParamShort(long invocationContext, int idx);


  static native int getParamInt(long invocationContext, int idx);


  static native float getParamFloat(long invocationContext, int idx);


  static native long getParamLong(long invocationContext, int idx);


  static native double getParamDouble(long invocationContext, int idx);


  static native Object getParamObject(long invocationContext, int idx);


//  static native void setParamBool(long invocationContext, int idx,boolean v);
//
//  static native void setParamChar(long invocationContext, int idx,char v);
//
//  static native void setParamByte(long invocationContext, int idx,byte v);
//
//  static native void setParamShort(long invocationContext, int idx,short v);


  static native void setParamInt(long invocationContext, int idx, int v);


  static native void setParamFloat(long invocationContext, int idx, float v);


  static native void setParamLong(long invocationContext, int idx, long v);


  static native void setParamDouble(long invocationContext, int idx, double v);


  static native void setParamObject(long invocationContext, int idx, Object v);


}
