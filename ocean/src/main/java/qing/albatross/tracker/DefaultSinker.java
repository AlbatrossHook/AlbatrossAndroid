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

import static qing.albatross.tracker.OceanLogger.log;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Member;
import java.util.concurrent.ConcurrentHashMap;

import qing.albatross.common.SafeToString;
import qing.albatross.common.ThreadConfig;
import qing.albatross.core.Albatross;

public class DefaultSinker extends OceanSinker {

  static final int METHOD_DESCRIPTION_CACHE_CAPACITY = 2048;

  // 全局 trace 时每个被追踪线程的每次返回都要走这里；用带访问序的 LinkedHashMap 加
  // synchronized 会让所有线程串行在一把 monitor 上，改为无锁读取、只在上限内写入。
  private final ConcurrentHashMap<Member, String> methodDescriptionCache =
      new ConcurrentHashMap<>(METHOD_DESCRIPTION_CACHE_CAPACITY);

  boolean onlyMainThread;

  @Override
  public boolean traceCurrentThread() {
    if (onlyMainThread)
      return Albatross.isMainThread();
    return true;
  }

  public DefaultSinker(boolean onlyMainThread) {
    this.onlyMainThread = onlyMainThread;
  }

  public DefaultSinker() {
    onlyMainThread = false;
  }

  @Override
  public void call(MethodCallContext context, Member member) {
    StringBuilder stringBuilder = ThreadConfig.stringBuilder();
    for (int i = 0; i < context.depth; i++) {
      stringBuilder.append("  ");
    }
    if (Albatross.isMainThread()) {
      stringBuilder.append("[").append(context.depth).append("]:");
    } else {
      stringBuilder.append("[").append(ThreadConfig.myId()).append(":").append(context.depth).append("]:");
    }
    stringBuilder.append(methodDescription(member));
    int beforeArguments = stringBuilder.length();
    stringBuilder.append(' ');
    if (!context.appendArguments(stringBuilder, 6000, 30, member instanceof Constructor<?>)) {
      stringBuilder.setLength(beforeArguments);
    }
    log(stringBuilder.toString());
  }

  @Override
  public void fieldRead(FieldReadEvent context, Member member, int dexPc, Object owner, Field field) {
    log("field:" + field);
  }

  @Override
  public void exec(TraceContext context, Member member, int dexPc, Object self) {
    log("exec:" + member.getName() + ":" + dexPc);
  }

  @Override
  public void fieldWritten(FieldWriteEvent context, Member member, int dexPc, Object owner, Field field, Object newValue) {
    log("fieldWritten:" + field + " value:" + newValue);
  }

  @Override
  public void block(TraceContext context, Member member, int dexPc, int offset) {
    log("block:" + member.getName() + ":" + dexPc + "->" + (dexPc + offset));
  }

  @Override
  public void ret(MethodReturnContext context, Member member, int dexPc, Object returnValue) {
    StringBuilder stringBuilder = ThreadConfig.stringBuilder();
//    for (int i = 0; i < context.depth; i++) {
//      stringBuilder.append("  ");
//    }
    if (Albatross.isMainThread()) {
      stringBuilder.append("[main]:");
    } else {
      stringBuilder.append("[").append(ThreadConfig.myId()).append("]:");
    }
    stringBuilder.append(methodDescription(member));
    if (returnValue != null) {
      stringBuilder.append(" ");
      SafeToString.safeToString(stringBuilder, returnValue, 760, 3);
    }
    appendArgumentsAndLog(context, member, stringBuilder);
  }

  @Override
  public void retPrimitive(MethodReturnContext context, Member member, int dexPc,
      Class<?> returnType, long rawValue) {
    StringBuilder stringBuilder = returnPrefix(member);
    if (returnType != null && returnType != void.class && returnType != Void.class) {
      stringBuilder.append(" ");
      SafeToString.appendPrimitive(stringBuilder, returnType, rawValue);
    }
    appendArgumentsAndLog(context, member, stringBuilder);
  }

  private StringBuilder returnPrefix(Member member) {
    StringBuilder stringBuilder = ThreadConfig.stringBuilder();
    if (Albatross.isMainThread()) {
      stringBuilder.append("[main]:");
    } else {
      stringBuilder.append("[").append(ThreadConfig.myId()).append("]:");
    }
    stringBuilder.append(methodDescription(member));
    return stringBuilder;
  }

  private void appendArgumentsAndLog(MethodReturnContext context, Member member,
      StringBuilder stringBuilder) {
    int beforeArguments = stringBuilder.length();
    stringBuilder.append(' ');
    if (!context.appendArguments(stringBuilder, 6000, 30, member instanceof Constructor<?>)) {
      stringBuilder.setLength(beforeArguments);
    }
    log(stringBuilder.toString());
  }

  private String methodDescription(Member member) {
    String description = methodDescriptionCache.get(member);
    if (description != null) {
      return description;
    }
    description = Albatross.methodToString(member);
    if (methodDescriptionCache.size() < METHOD_DESCRIPTION_CACHE_CAPACITY) {
      methodDescriptionCache.putIfAbsent(member, description);
    }
    return description;
  }

  public int getMethodDescriptionCacheSize() {
    return methodDescriptionCache.size();
  }

  @Override
  public void onUnTrace() {
    methodDescriptionCache.clear();
  }
}
