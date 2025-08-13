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

import qing.albatross.annotation.Alias;
import qing.albatross.annotation.ByName;
import qing.albatross.reflection.MethodDef;
import qing.albatross.reflection.VoidMethodDef;


public class MethodCallHook {
  Member member;
  long listenerId = 0;
  ReturnType returnType;
  MethodCallback callback;

  public synchronized void unHook() {
    if (listenerId != 0) {
      Albatross.unHookMethodNative(listenerId);
      listenerId = 0;
    }
  }


  @Alias("callVoid")
  private void callVoid(long invocationContext) {
    callback.call(new CallFrame(this, invocationContext));
  }


  static class Image {
    @ByName
    public static VoidMethodDef callVoid;
    @ByName
    public static MethodDef<Boolean> callBool;
    @ByName
    public static MethodDef<Character> callChar;
    @ByName
    public static MethodDef<Byte> callByte;
    @ByName
    public static MethodDef<Short> callShort;
    @ByName
    public static MethodDef<Integer> callInt;
    @ByName
    public static MethodDef<Float> callFloat;
    @ByName
    public static MethodDef<Long> callLong;
    @ByName
    public static MethodDef<Double> callDouble;
    @ByName
    public static MethodDef<Object> callObject;

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


}
