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

public class InvocationContext {
  private final long invocationContext;
  private final InstructionListener listener;

  public InvocationContext(long invocationContext, InstructionListener listener) {
    this.invocationContext = invocationContext;
    this.listener = listener;
  }


  public int NumberOfVRegs() {
    return listener.getNumberVRegs(invocationContext);
  }

  public float GetVRegFloat(int i) {
    assert i < NumberOfVRegs();
    return InstructionListener.GetVRegFloat(invocationContext, i);
  }

  public long GetVRegLong(int i) {
    assert i < NumberOfVRegs();
    return InstructionListener.GetVRegLong(invocationContext, i);
  }

  public Object GetVRegReference(int i) {
    assert i < NumberOfVRegs();
    return InstructionListener.GetVRegReference(invocationContext, i);
  }

  public int GetVReg(int i) {
    assert i < NumberOfVRegs();
    return InstructionListener.GetVReg(invocationContext, i);
  }

  public short GetVRegShort(int i) {
    assert i < NumberOfVRegs();
    return (short) GetVReg(i);
  }

  public boolean GetVRegBool(int i) {
    assert i < NumberOfVRegs();
    return GetVReg(i) != 0;
  }


  public float GetParamFloat(int i) {
    i += listener.getFirstArgReg(invocationContext);
    assert i < NumberOfVRegs();
    return InstructionListener.GetVRegFloat(invocationContext, i);
  }

  public long GetParamLong(int i) {
    i += listener.getFirstArgReg(invocationContext);
    assert i < NumberOfVRegs();
    return InstructionListener.GetVRegLong(invocationContext, i);
  }

  public Object GetParamReference(int i) {
    i += listener.getFirstArgReg(invocationContext);
    assert i < NumberOfVRegs();
    return InstructionListener.GetVRegReference(invocationContext, i);
  }

  public int GetParamInt(int i) {
    i += listener.getFirstArgReg(invocationContext);
    assert i < NumberOfVRegs();
    return InstructionListener.GetVReg(invocationContext, i);
  }

  public short GetParamShort(int i) {
    i += listener.getFirstArgReg(invocationContext);
    assert i < NumberOfVRegs();
    return (short) GetVReg(i);
  }

  public boolean GetParamBool(int i) {
    i += listener.getFirstArgReg(invocationContext);
    assert i < NumberOfVRegs();
    return GetVReg(i) != 0;
  }


  public int SetVReg(int i, int val) {
    assert i < NumberOfVRegs();
    return InstructionListener.SetVReg(invocationContext, i, val);
  }

  public int SetVRegBoolean(int i, boolean val) {
    assert i < NumberOfVRegs();
    return InstructionListener.SetVReg(invocationContext, i, val ? 1 : 0);
  }

  public float SetVRegFloat(int i, float val) {
    assert i < NumberOfVRegs();
    return InstructionListener.SetVRegFloat(invocationContext, i, val);
  }

  public long SetVRegLong(int i, long val) {
    assert i < NumberOfVRegs();
    return InstructionListener.SetVRegLong(invocationContext, i, val);
  }

  public double SetVRegDouble(int i, double val) {
    assert i < NumberOfVRegs();
    return InstructionListener.SetVRegDouble(invocationContext, i, val);
  }

  public void SetVRegReference(int i, Object val) {
    assert i < NumberOfVRegs();
    InstructionListener.SetVRegReference(invocationContext, i, val);
  }

}
