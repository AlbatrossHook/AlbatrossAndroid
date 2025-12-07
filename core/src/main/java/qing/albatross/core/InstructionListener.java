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


public abstract class InstructionListener extends MethodInvokeFrame {

  long listenerId = 0;

  abstract public void onEnter(Member method, Object self, int dexPc, InvocationContext invocationContext);

  public void onReturn(Member method, Object ret, int dexPc, InvocationContext invocationContext) {
  }

  @Override
  public synchronized boolean unHook() {
    if (listenerId != 0) {
      unHookInstructionNative(listenerId);
      listenerId = 0;
      return true;
    }
    return false;
  }

  //All these native methods register by Albatross.registerMethodNative

  static native long hookInstructionNative(Member member, int minDexPc, int maxDexPc, Object callback);

  static native void unHookInstructionNative(long listenerId);

  public static native int NumberOfVRegs(long invocationContext);

  public static native float GetVRegFloat(long invocationContext, int i);

  public static native long GetVRegLong(long invocationContext, int i);

  public static native double GetVRegDouble(long invocationContext, int i);

  public static native Object GetVRegReference(long invocationContext, int i);

  public static native int GetVReg(long invocationContext, int i);

  public static native int SetVReg(long invocationContext, int i, int val);

  public static native float SetVRegFloat(long invocationContext, int i, float val);

  public static native long SetVRegLong(long invocationContext, int i, long val);

  public static native double SetVRegDouble(long invocationContext, int i, double val);

  public static native void SetVRegReference(long invocationContext, int i, Object val);

  static native String dumpSmaliString(long invocationContext, int dexPc);

  @Alias("onEnter")
  private void onEnter(Object self, int dexPc, long invocationContext) {
    onEnter(member, self, dexPc, new InvocationContext(invocationContext, this));
  }
}
