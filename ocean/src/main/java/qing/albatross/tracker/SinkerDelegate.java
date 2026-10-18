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


import static qing.albatross.core.MethodInvokeFrame.boxPrim;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Member;
import java.lang.reflect.Method;

import qing.albatross.annotation.Alias;
import qing.albatross.common.ThreadConfig;
import qing.albatross.core.Albatross;

public class SinkerDelegate {
  OceanSinker sinker;
  ThreadLocal<TraceContext> traceContext;
  ThreadLocal<MethodCallContext> callContext;
  ThreadLocal<MethodReturnContext> returnContext;
  ThreadLocal<FieldReadEvent> fieldReadEvent;
  ThreadLocal<FieldWriteEvent> fieldWriteEvent;

  /**
   * 追踪窗口检查(超时/事件数上限): 每个事件回调入口调用一次。
   * 运行在被追踪线程上, 所以停止可以直接对当前线程生效。
   * 返回 true 表示窗口已结束, 本事件丢弃。
   */
  private boolean windowClosed() {
    int reason = OceanTracker.checkWindow(sinker, OceanTracker.countWindowEvent());
    if (reason == OceanTracker.REASON_NONE)
      return false;
    OceanTracker.requestStop(sinker, reason);
    return true;
  }

  @Alias("onCall")
  private void onCall(Member member, long invocationId, int depth) {
    if (windowClosed())
      return;
    try {
      MethodCallContext context = callContext.get();
      if (context == null) {
        context = new MethodCallContext();
        callContext.set(context);
        Albatross.log("why context is null：" + member, new Exception("onCall"));
      }
      context.setInvocationContext(invocationId, member);
      context.depth = depth;
      sinker.callCount++;
      sinker.call(context, member);
    } catch (Throwable e) {
      Albatross.log("onCall err", e);
      OceanTracker.stopTraceMe();
    }
  }

  @Alias("onExec")
  private void onExec(Member member, int dexPc, Object self, long invocationId) {
    if (windowClosed())
      return;
    try {
      TraceContext context = traceContext.get();
      if (context == null) {
        context = new TraceContext();
        traceContext.set(context);
      }
      context.setInvocationContext(invocationId, member);
      sinker.exec(context, member, dexPc, self);
    } catch (Throwable e) {
      Albatross.log("onExec err", e);
      OceanTracker.stopTraceMe();
    }

  }


  @Alias("onBlock")
  private void onBlock(Member member, int dexPc, int offset, long invocationId) {
    if (windowClosed())
      return;
    try {
      TraceContext context = traceContext.get();
      if (context == null) {
        context = new TraceContext();
        traceContext.set(context);
      }
      context.setInvocationContext(invocationId, member);
      sinker.block(context, member, dexPc, offset);
    } catch (Throwable e) {
      Albatross.log("onBlock err", e);
      OceanTracker.stopTraceMe();
    }

  }


  @Alias("onFieldRead")
  private void onFieldRead(Member member, int dexPc, Object owner, Field field, long invocationId) {
    if (windowClosed())
      return;
    try {
      FieldReadEvent context = fieldReadEvent.get();
      if (context == null) {
        context = new FieldReadEvent();
        fieldReadEvent.set(context);
      }
      context.setInvocationContext(invocationId, member);
      sinker.fieldRead(context, member, dexPc, owner, field);
    } catch (Throwable e) {
      Albatross.log("onFieldRead err", e);
      OceanTracker.stopTraceMe();
    }

  }

  @Alias("onFieldWritten")
  private void onFieldWritten(Member member, int dexPc, Object owner, Field field, Object newValue, long invocationId) {
    if (windowClosed())
      return;
    try {
      FieldWriteEvent context = fieldWriteEvent.get();
      if (context == null) {
        context = new FieldWriteEvent();
        fieldWriteEvent.set(context);
      }
      context.setInvocationContext(invocationId, member);
      sinker.fieldWritten(context, member, dexPc, owner, field, newValue);
    } catch (Exception e) {
      Albatross.log("onFieldWritten err", e);
      OceanTracker.stopTraceMe();
    }

  }



  @Alias("onFieldWrittenPrim")
  private void onFieldWrittenPrim(Member member, int dexPc, Object self, Field field, long newValue, long invocationId) {
    if (windowClosed())
      return;
    try {
      Object valueObject = boxPrim(field.getType(), newValue);
      FieldWriteEvent context = fieldWriteEvent.get();
      if (context == null) {
        context = new FieldWriteEvent();
        fieldWriteEvent.set(context);
      }
      context.setInvocationContext(invocationId, member);
      sinker.fieldWritten(context, member, dexPc, self, field, valueObject);
    } catch (Exception e) {
      Albatross.log("onFieldWrittenPrim err", e);
      OceanTracker.stopTraceMe();
    }
  }


  @Alias("onReturn")
  private void onReturn(Member member, int dexPc, Object returnValue, long invocationId) {
    if (windowClosed())
      return;
    try {
      MethodReturnContext context = returnContext.get();
      if (context == null) {
        context = new MethodReturnContext();
        returnContext.set(context);
      }
      context.setInvocationContext(invocationId, member);
      sinker.callCount++;
      sinker.ret(context, member, dexPc, returnValue);
    } catch (Throwable e) {
      Albatross.log("onReturn err", e);
      OceanTracker.stopTraceMe();
    }
  }

  @Alias("onReturnPrim")
  private void onReturnPrim(Member member, int dexPc, long returnValue, long invocationId) {
    if (windowClosed())
      return;
    try {
      MethodReturnContext context = returnContext.get();
      if (context == null) {
        context = new MethodReturnContext();
        returnContext.set(context);
      }
      context.setInvocationContext(invocationId, member);
      if (member instanceof Constructor) {
        sinker.callCount++;
        sinker.retPrimitive(context, member, dexPc, void.class, returnValue);
      } else {
        sinker.callCount++;
        Method method = (Method) member;
        sinker.retPrimitive(context, member, dexPc, method.getReturnType(), returnValue);
      }
    } catch (Throwable e) {
      Albatross.log("onReturnPrim err", e);
      OceanTracker.stopTraceMe();
    }

  }

  @Alias("onUnTrace")
  private void onUnTrace() {
    if (sinker != null) {
      try {
        sinker.onUnTrace();
      } catch (Throwable e) {
        Albatross.log("onUnTrace sinker err", e);
      }
    }
    if (traceContext != null) {
      traceContext.remove();
    }
    if (callContext != null) {
      callContext.remove();
    }
    if (returnContext != null) {
      returnContext.remove();
    }
    if (fieldReadEvent != null) {
      fieldReadEvent.remove();
    }
    if (fieldWriteEvent != null) {
      fieldWriteEvent.remove();
    }
  }

  @Alias("onTrace")
  private boolean onTrace() {
    if (!sinker.traceCurrentThread())
      return false;
    if (!ThreadConfig.canTraceMe())
      return false;
    if (traceContext != null) {
      traceContext.set(new TraceContext());
    }
    if (callContext != null) {
      callContext.set(new MethodCallContext());
    }
    if (returnContext != null) {
      returnContext.set(new MethodReturnContext());
    }
    if (fieldReadEvent != null) {
      fieldReadEvent.set(new FieldReadEvent());
    }
    if (fieldWriteEvent != null) {
      fieldWriteEvent.set(new FieldWriteEvent());
    }
    return true;
  }


}
