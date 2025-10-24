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


public class InstructionListener extends MethodInvokeFrame {

  InstructionCallback callback;
  long listenerId = 0;


  public synchronized void unHook() {
    if (listenerId != 0) {
      unHookInstructionNative(listenerId);
      listenerId = 0;
    }
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
    callback.onEnter(member, self, dexPc, new InvocationContext(invocationContext, this));
  }
}
