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
import qing.albatross.common.ThreadConfig;
import qing.albatross.core.Albatross;

public class StringSinker extends OceanSinker {


  String keyword;


  public StringSinker(String keyword) {
    this.keyword = keyword.toLowerCase();
  }


  @Override
  public void call(MethodCallContext context, Member member) {
    Object[] arguments = context.getStringArguments();
    if (arguments != null) {
      for (Object o : arguments) {
        String s;
        if (o == null)
          return;
        if (o instanceof String) {
          s = (String) o;
        } else
          s = o.toString();
        if (s.toLowerCase().contains(keyword)) {
          StringBuilder stringBuilder = new StringBuilder();
          for (int i = 0; i < context.depth; i++) {
            stringBuilder.append("  ");
          }
          if (Albatross.isMainThread()) {
            stringBuilder.append("[").append(context.depth).append("]:");
          } else {
            stringBuilder.append("[").append(ThreadConfig.myId()).append(":").append(context.depth).append("]:");
          }
          stringBuilder.append(member.toString());
          String arrayToString = SafeToString.arrayToString(arguments);
          OceanLogger.log(stringBuilder + " " + arrayToString);
          return;
        }
      }
    }
  }

  @Override
  public void fieldRead(FieldReadEvent context, Member member, int dexPc, Object owner, Field field) {
    OceanLogger.log("field:" + field);
  }

  @Override
  public void exec(TraceContext context, Member member, int dexPc, Object self) {
    OceanLogger.log("exec:" + member.getName() + ":" + dexPc);
  }

  @Override
  public void fieldWritten(FieldWriteEvent context, Member member, int dexPc, Object owner, Field field, Object newValue) {
    OceanLogger.log("fieldWritten:" + field + " value:" + newValue);
  }

  @Override
  public void block(TraceContext context, Member member, int dexPc, int offset) {
    OceanLogger.log("block:" + member.getName() + ":" + dexPc + "->" + (dexPc + offset));
  }

  @Override
  public void ret(MethodReturnContext context, Member member, int dexPc, Object returnValue) {
    OceanLogger.log("return:" + member.getName() + ":" + dexPc + " value:" + returnValue);
  }
}
