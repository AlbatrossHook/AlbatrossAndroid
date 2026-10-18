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

import qing.albatross.common.SafeToString;
import qing.albatross.core.Albatross;

public class EmptySinker extends OceanSinker {


  public EmptySinker() {
  }


  @Override
  public void call(MethodCallContext context, Member member) {
    if ((callCount & 127) == 1) {
      Albatross.log("callCount:" + callCount);
    }
  }


  @Override
  public void fieldRead(FieldReadEvent context, Member member, int dexPc, Object owner, Field field) {
    Albatross.log("field:" + field);
  }

  @Override
  public void exec(TraceContext context, Member member, int dexPc, Object self) {
    Albatross.log("exec:" + member.getName() + ":" + dexPc);
  }

  @Override
  public void fieldWritten(FieldWriteEvent context, Member member, int dexPc, Object owner, Field field, Object newValue) {
    Albatross.log("fieldWritten:" + field + " value:" + newValue);
  }

  @Override
  public void block(TraceContext context, Member member, int dexPc, int offset) {
    Albatross.log("block:" + member.getName() + ":" + dexPc + "->" + (dexPc + offset));
  }

  @Override
  public void ret(MethodReturnContext context, Member member, int dexPc, Object returnValue) {
    Albatross.log("return:" + member.getName() + ":" + dexPc + " value:" + returnValue);
  }
}
