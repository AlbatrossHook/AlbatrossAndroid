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
package qing.albatross.tracker;

import java.lang.reflect.Field;
import java.lang.reflect.Member;

import qing.albatross.core.MethodInvokeFrame;

public abstract class OceanSinker {

  public int callCount;

  public void fieldWritten(FieldWriteEvent context, Member member, int dexPc, Object owner, Field field, Object newValue) {
  }

  public void fieldRead(FieldReadEvent context, Member member, int dexPc, Object owner, Field field) {
  }

  public void block(TraceContext context, Member member, int dexPc, int offset) {
  }

  public abstract void call(MethodCallContext context, Member member);

  public void exec(TraceContext context, Member member, int dexPc, Object self) {
  }

  public void ret(MethodReturnContext context, Member member, int dexPc, Object returnValue) {
  }

  /** Primitive return callback. Subclasses can consume the raw register value without boxing. */
  public void retPrimitive(MethodReturnContext context, Member member, int dexPc,
      Class<?> returnType, long rawValue) {
    ret(context, member, dexPc, MethodInvokeFrame.boxPrim(returnType, rawValue));
  }

  /** Called when the native trace for this sinker is stopped. */
  public void onUnTrace() {
  }

  public boolean traceCurrentThread() {
    return true;
  }


  public OceanSinker() {
  }


}
