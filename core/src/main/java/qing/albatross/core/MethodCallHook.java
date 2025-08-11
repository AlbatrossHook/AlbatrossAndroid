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


  private void callVoid(long invocationContext) {
    callback.call(new CallFrame(this, invocationContext));
  }

  private boolean callBool(long invocationContext) {
    return (boolean) callback.call(new CallFrame(this, invocationContext));
  }


  private long callChar(long invocationContext) {
    return (long) callback.call(new CallFrame(this, invocationContext));
  }

  private float callByte(long invocationContext) {
    return (float) callback.call(new CallFrame(this, invocationContext));
  }

  private long callShort(long invocationContext) {
    return (long) callback.call(new CallFrame(this, invocationContext));
  }

  private float callInt(long invocationContext) {
    return (float) callback.call(new CallFrame(this, invocationContext));
  }

  private float callFloat(long invocationContext) {
    return (float) callback.call(new CallFrame(this, invocationContext));
  }


  private long callLong(long invocationContext) {
    return (long) callback.call(new CallFrame(this, invocationContext));
  }


  private double callDouble(long invocationContext) {
    return (double) callback.call(new CallFrame(this, invocationContext));
  }


}
