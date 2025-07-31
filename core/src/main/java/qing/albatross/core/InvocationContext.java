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
  private final long invocationContext;
  private final InstructionListener listener;

  public InvocationContext(long invocationContext, InstructionListener listener) {
    this.invocationContext = invocationContext;
    this.listener = listener;
  }


  public int numberOfVRegs() {
    return listener.getNumberVRegs(invocationContext);
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
    return InstructionListener.GetVReg(invocationContext, listener.getArgReg(invocationContext, i)) != 0;
  }

  public char getParamChar(int i) {
    return (char) InstructionListener.GetVReg(invocationContext, listener.getArgReg(invocationContext, i));
  }

  public byte getParamByte(int i) {
    return (byte) InstructionListener.GetVReg(invocationContext, listener.getArgReg(invocationContext, i));
  }

  public short getParamShort(int i) {
    return (short) InstructionListener.GetVReg(invocationContext, listener.getArgReg(invocationContext, i));
  }


  public int getParamInt(int i) {
    return InstructionListener.GetVReg(invocationContext, listener.getArgReg(invocationContext, i));
  }

  public float getParamFloat(int i) {
    return InstructionListener.GetVRegFloat(invocationContext, listener.getArgReg(invocationContext, i));
  }

  public long getParamLong(int i) {
    return InstructionListener.GetVRegLong(invocationContext, listener.getArgRegTwoWord(invocationContext, i));
  }

  public double getParamDouble(int i) {
    return InstructionListener.GetVRegDouble(invocationContext, listener.getArgRegTwoWord(invocationContext, i));
  }

  public Object getParamObject(int i) {
    return InstructionListener.GetVRegReference(invocationContext, listener.getArgReg(invocationContext, i));
  }

  public Object getThis() {
    if (Modifier.isStatic(listener.member.getModifiers()))
      return null;
    return getParamBool(0);
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


  public int setParamBoolean(int i, boolean val) {
    return InstructionListener.SetVReg(invocationContext, listener.getArgReg(invocationContext, i), val ? 1 : 0);
  }

  public int setParamChar(int i, char val) {
    return InstructionListener.SetVReg(invocationContext, listener.getArgReg(invocationContext, i), val);
  }

  public int setParamByte(int i, byte val) {
    return (byte) InstructionListener.SetVReg(invocationContext, listener.getArgReg(invocationContext, i), val);
  }

  public int setParamShort(int i, short val) {
    return InstructionListener.SetVReg(invocationContext, listener.getArgReg(invocationContext, i), val);
  }


  public int setParamInt(int i, int val) {
    return InstructionListener.SetVReg(invocationContext, listener.getArgReg(invocationContext, i), val);
  }

  public float setParamFloat(int i, float val) {
    return InstructionListener.SetVRegFloat(invocationContext, listener.getArgReg(invocationContext, i), val);
  }

  public long setParamLong(int i, long val) {
    return InstructionListener.SetVRegLong(invocationContext, listener.getArgRegTwoWord(invocationContext, i), val);
  }

  public double setParamDouble(int i, double val) {
    return InstructionListener.SetVRegDouble(invocationContext, listener.getArgRegTwoWord(invocationContext, i), val);
  }

  public void setParamObject(int i, Object val) {
    InstructionListener.SetVRegReference(invocationContext, listener.getArgReg(invocationContext, i), val);
  }


}
