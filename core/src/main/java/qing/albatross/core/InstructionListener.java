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
import java.lang.reflect.Modifier;


public class InstructionListener {
  Member member;
  InstructionCallback callback;
  long listenerId = 0;
  int numberVRegs = -1;
  int first_arg_reg = -1;


  public synchronized void unHook() {
    if (listenerId != 0) {
      unHookInstructionNative(listenerId);
      listenerId = 0;
    }
  }

  public int getNumberVRegs(long invocationContext) {
    if (numberVRegs >= 0)
      return numberVRegs;
    numberVRegs = NumberOfVRegs(invocationContext);
    return numberVRegs;
  }

  public int getFirstArgReg(long invocationContext) {
    if (first_arg_reg >= 0)
      return first_arg_reg;
    if (member instanceof Method) {
      Method method = (Method) member;
      int arg_count = method.getParameterCount();
      if (!Modifier.isStatic(method.getModifiers()))
        arg_count += 1;
      first_arg_reg = getNumberVRegs(invocationContext) - arg_count;
    }
    return first_arg_reg;
  }

  public int getArgReg(long invocationContext, int i) {
    int idx = getFirstArgReg(invocationContext) + i;
    assert idx < numberVRegs;
    return idx;
  }

  public int getArgRegTwoWord(long invocationContext, int i) {
    int idx = getFirstArgReg(invocationContext) + i;
    assert idx + 1 < numberVRegs;
    return idx;
  }

  static native long hookInstructionNative(Member member, int minDexPc, int maxDexPc, Object callback);

  static native void unHookInstructionNative(long listenerId);

  static native int NumberOfVRegs(long invocationContext);

  static native float GetVRegFloat(long invocationContext, int i);

  static native long GetVRegLong(long invocationContext, int i);

  static native double GetVRegDouble(long invocationContext, int i);

  static native Object GetVRegReference(long invocationContext, int i);

  static native int GetVReg(long invocationContext, int i);

  static native int SetVReg(long invocationContext, int i, int val);

  static native float SetVRegFloat(long invocationContext, int i, float val);

  static native long SetVRegLong(long invocationContext, int i, long val);

  static native double SetVRegDouble(long invocationContext, int i, double val);

  static native void SetVRegReference(long invocationContext, int i, Object val);


  private void onEnter(Object self, int dexPc, long invocationContext) {
    callback.onEnter(member, self, dexPc, new InvocationContext(invocationContext, this));
  }
}
