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

import qing.albatross.annotation.Alias;

public class MethodCallContext extends InvokeContext {

  public long depth;


  public void bypass() {
    OceanTracker.processMethod(invocationContext, true);
  }

  public void returnWithResult(Object object) {

  }

  public void returnLong(long object) {

  }

  public void returnDouble(double object) {

  }

  @Alias(("returnInt"))
  public void returnInt(int ret) {

  }

  public native void returnChar(char ret);

  public native void returnShort(short ret);

  public native void returnByte(byte ret);

  public native void returnFloat(float ret);


  public void skipCall() {

  }


  @Override
  public int getEventType() {
    return EventType.CALL;
  }
}
