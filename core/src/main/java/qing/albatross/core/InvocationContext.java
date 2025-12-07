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

import java.lang.reflect.Modifier;

public class InvocationContext {
  protected long invocationContext;
  protected final MethodInvokeFrame methodFrame;

  public InvocationContext(long invocationContext, MethodInvokeFrame listener) {
    this.invocationContext = invocationContext;
    this.methodFrame = listener;
  }

  public boolean unHook() {
    return methodFrame.unHook();
  }


  public int numberOfVRegs() {
    return methodFrame.getNumberVRegs(invocationContext);
  }

  public String smaliString() {
    return InstructionListener.dumpSmaliString(invocationContext, -1);
  }

  public boolean getVRegBool(int i) {
    return getVRegInt(i) != 0;
  }

  public char getVRegChar(int i) {
    return (char) getVRegInt(i);
  }

  public byte getVRegByte(int i) {
    return (byte) getVRegInt(i);
  }


  public short getVRegShort(int i) {
    return (short) getVRegInt(i);
  }

  public int getVRegInt(int i) {
    assert i < numberOfVRegs();
    return InstructionListener.GetVReg(invocationContext, i);
  }

  public float getVRegFloat(int i) {
    assert i < numberOfVRegs();
    return InstructionListener.GetVRegFloat(invocationContext, i);
  }

  public long getVRegLong(int i) {
    assert i + 1 < numberOfVRegs();
    return InstructionListener.GetVRegLong(invocationContext, i);
  }

  public double getVRegDouble(int i) {
    assert i + 1 < numberOfVRegs();
    return InstructionListener.GetVRegDouble(invocationContext, i);
  }

  public Object getVRegObject(int i) {
    assert i < numberOfVRegs();
    return InstructionListener.GetVRegReference(invocationContext, i);
  }


  public boolean getParamBool(int i) {
    return methodFrame.getParamPrim(invocationContext, i, short.class) != 0;
  }

  public char getParamChar(int i) {
    return (char) methodFrame.getParamPrim(invocationContext, i, char.class);
  }

  public byte getParamByte(int i) {
    return (byte) methodFrame.getParamPrim(invocationContext, i, byte.class);
  }

  public short getParamShort(int i) {

    return (short) methodFrame.getParamPrim(invocationContext, i, short.class);
  }


  public int getParamInt(int i) {
    return methodFrame.getParamPrim(invocationContext, i, int.class);
  }

  public float getParamFloat(int i) {
    return methodFrame.getParamFloat(invocationContext, i);
  }

  public long getParamLong(int i) {
    return methodFrame.getParamLong(invocationContext, i);
  }

  public double getParamDouble(int i) {
    return methodFrame.getParamDouble(invocationContext, i);
  }

  public <T> T getParamObject(int i, Class<T> clz) {
    return methodFrame.getParamObject(invocationContext, i, clz);
  }

  public <T> T getThis(Class<T> clz) {
    if (Modifier.isStatic(methodFrame.member.getModifiers()))
      return null;
    return getParamObject(0, clz);
  }

  public Object getThis() {
    if (Modifier.isStatic(methodFrame.member.getModifiers()))
      return null;
    return getParamObject(0, Object.class);
  }


  public int setVRegBool(int i, boolean val) {
    assert i < numberOfVRegs();
    return InstructionListener.SetVReg(invocationContext, i, val ? 1 : 0);
  }

  public int setVRegChar(int i, char val) {
    assert i < numberOfVRegs();
    return InstructionListener.SetVReg(invocationContext, i, val);
  }

  public int setVRegByte(int i, byte val) {
    assert i < numberOfVRegs();
    return InstructionListener.SetVReg(invocationContext, i, val);
  }

  public int setVRegShort(int i, short val) {
    assert i < numberOfVRegs();
    return InstructionListener.SetVReg(invocationContext, i, val);
  }

  public int setVRegInt(int i, int val) {
    assert i < numberOfVRegs();
    return InstructionListener.SetVReg(invocationContext, i, val);
  }

  public float setVRegFloat(int i, float val) {
    assert i < numberOfVRegs();
    return InstructionListener.SetVRegFloat(invocationContext, i, val);
  }

  public long setVRegLong(int i, long val) {
    assert i + 1 < numberOfVRegs();
    return InstructionListener.SetVRegLong(invocationContext, i, val);
  }

  public double setVRegDouble(int i, double val) {
    assert i + 1 < numberOfVRegs();
    return InstructionListener.SetVRegDouble(invocationContext, i, val);
  }

  public void setVRegObject(int i, Object val) {
    assert i < numberOfVRegs();
    InstructionListener.SetVRegReference(invocationContext, i, val);
  }


  public boolean setParamBoolean(int i, boolean val) {
    return methodFrame.setParamPrim(invocationContext, i, val ? 1 : 0, boolean.class) != 0;
  }

  public int setParamChar(int i, char val) {
    return methodFrame.setParamPrim(invocationContext, i, val, char.class);
  }

  public int setParamByte(int i, byte val) {
    return methodFrame.setParamPrim(invocationContext, i, val, byte.class);
  }

  public int setParamShort(int i, short val) {
    return methodFrame.setParamPrim(invocationContext, i, val, short.class);
  }


  public int setParamInt(int i, int val) {
    return methodFrame.setParamPrim(invocationContext, i, val, int.class);
  }

  public Class<?>[] getParamTypes() {
    return methodFrame.getParameterTypes(invocationContext);
  }

  public float setParamFloat(int i, float val) {
    return methodFrame.setParamFloat(invocationContext, i, val);
  }

  public long setParamLong(int i, long val) {
    return methodFrame.setParamLong(invocationContext, i, val);
  }

  public double setParamDouble(int i, double val) {
    return methodFrame.setParamDouble(invocationContext, i, val);
  }

  public void setParamObject(int i, Object val) {
    methodFrame.setParamObject(invocationContext, i, val);
  }

  public Object[] getArguments() {
    return methodFrame.getArguments(invocationContext);
  }

  public Object[] getStringArguments() {
    return methodFrame.getStringArguments(invocationContext);
  }

  public Object[] getToStringArguments() {
    return methodFrame.getToStringArguments(invocationContext);
  }


}
