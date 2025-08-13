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
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

public class CallFrame {
  long contextId;


  public CallFrame(MethodCallHook callHook, long contextId) {
    this.callHook = callHook;
    this.contextId = contextId;
  }

  MethodCallHook callHook;


  public boolean invokeBool() {
    throw new RuntimeException("not impl");
  }

  public char invokeChar() {
    throw new RuntimeException("not impl");
  }

  public byte invokeByte() {
    throw new RuntimeException("not impl");
  }

  public short invokeShort() {
    throw new RuntimeException("not impl");
  }

  public int invokeInt() {
    assert callHook.returnType == ReturnType.INT;
    return MethodCallHook.invokeInt(contextId);
  }

  public float invokeFloat() {
    throw new RuntimeException("not impl");
  }

  public long invokeLong() {
    throw new RuntimeException("not impl");
  }

  public double invokeDouble() {
    throw new RuntimeException("not impl");
  }

  public Object invokeObject() {
    throw new RuntimeException("not impl");
  }

  public Member getMember() {
    return callHook.member;
  }

  public Method getMethod() {
    return (Method) callHook.member;
  }

  public Constructor<?> getConstructor() {
    return (Constructor<?>) callHook.member;
  }

  public void invoke() {
    throw new RuntimeException("not impl");
  }

  public int setParamBoolean(int i, boolean val) {
    throw new RuntimeException("not impl");
  }

  public int setParamChar(int i, char val) {
    throw new RuntimeException("not impl");
  }

  public int setParamByte(int i, byte val) {
    throw new RuntimeException("not impl");
  }

  public int setParamShort(int i, short val) {
    throw new RuntimeException("not impl");
  }


  public int setParamInt(int i, int val) {
    throw new RuntimeException("not impl");
  }


  public float setParamFloat(int i, float val) {
    throw new RuntimeException("not impl");
  }

  public long setParamLong(int i, long val) {
    throw new RuntimeException("not impl");
  }

  public double setParamDouble(int i, double val) {
    throw new RuntimeException("not impl");
  }

  public void setParamObject(int i, Object val) {
    throw new RuntimeException("not impl");
  }


  public boolean getParamBool(int i) {
    throw new RuntimeException("not impl");
  }

  public char getParamChar(int i) {
    throw new RuntimeException("not impl");
  }

  public byte getParamByte(int i) {
    throw new RuntimeException("not impl");
  }

  public short getParamShort(int i) {
    throw new RuntimeException("not impl");
  }


  public int getParamInt(int i) {
    throw new RuntimeException("not impl");
  }

  public float getParamFloat(int i) {
    throw new RuntimeException("not impl");
  }

  public long getParamLong(int i) {
    throw new RuntimeException("not impl");
  }

  public double getParamDouble(int i) {
    throw new RuntimeException("not impl");
  }

  public Object getParamObject(int i) {
    throw new RuntimeException("not impl");
  }

  public Object getThis() {
    if (Modifier.isStatic(callHook.member.getModifiers()))
      return null;
    return getParamObject(0);
  }


}
