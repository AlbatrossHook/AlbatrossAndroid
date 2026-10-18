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
package qing.albatross.nativehook;

import static qing.albatross.common.Const.*;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import qing.albatross.annotation.FuncBackup;
import qing.albatross.annotation.FuncHook;
import qing.albatross.annotation.Symbol;
import qing.albatross.annotation.TargetLibrary;
import qing.albatross.core.Albatross;

public class AlbNative {


  static native long dlopen(String libName);

  static native long dlsym(long obj, String symbol);

  static native long iterSymbol(long obj, Object data);

  static native long getModuleBase(long obj);

  public static native void watchFunc(String symbol, long func);

  static native void dlclose(long obj);

  static native void iterLib(Object callback);

  public static native void hookInit(String logPath);

  public static native boolean dumpNativeMethod(String outputPath);

  static native void registerLibraryCallbackNative(Object callback, String libName);

  static native long hookInstructionNative(long address, Object onEnter, Object onLeave, Object userdata);

  // 延迟 hook: 目标库尚未加载时先登记, 加载完成后自动安装(见 DelayedHookRecord)
  // symbol 或 offset 二选一: symbol 为 null 时按 offset(ELF vaddr)登记
  static native long hookInstrumentDelayedNative(String lib, String symbol, long offset,
                                                 Object onEnter, Object onLeave, Object userdata, Object record);

  static native long delayedHookIdNative(long handle);

  static native int delayedHookStateNative(long id);

  static native long delayedHookAddressNative(long id);

  static native long delayedHookBackupNative(long id);

  static native boolean unHookDelayedNative(long handle);

  static native long backupMethodNative(long address, Object method, byte[] args, byte retType);

  static native long hookFuncNative(long address, Object method, byte[] args, byte retType);

  static native boolean unHookFuncNative(long handle);

  static native boolean unBackupMethodNative(Object address);

  static native boolean unHookInstructionNative(long address);

  // CModule: 进程内 tcc 动态编译; 失败返回 0, 错误文本写入 errorOut[0]
  static native long cmoduleCompileNative(String source, String options,
                                          String[] symbolNames, long[] symbolAddrs, String[] errorOut);

  static native long cmoduleGetSymbolNative(long handle, String name);

  static native void cmoduleFreeNative(long handle);

  // NativeFunction: 按签名调用任意 native 地址, 见 qing.albatross.nativehook.NativeFunction
  static native Object dynamicCallNative(long address, byte retType, byte[] argTypes, Object[] args);

  static native long getNthArgumentNative(long invokeContext, int nth);

  static native void setNthArgumentNative(long invokeContext, int nth, long value);

  static native long getRegValueNative(long invokeContext, String reg);

  static native void setRegValueNative(long invokeContext, String reg, long value);

  static native void setReturnResultNative(long invokeContext, long value);

  static native long getReturnResultNative(long invokeContext);

  static native Object readObject(long address, Class<?> c);

  static native boolean readableNative(long address);

  static native void freeAddress(long address);

  static native long mallocNative(int size);

  static native long callocNative(int size);

  public static native long memset(long address, int v, int size);

  static native byte[] readBytes(long address, int length);

  static native boolean writeBytes(long address, byte[] data);

  static native int[] readInts(long address, int length);

  static native boolean writeInts(long address, int[] data);

  static native boolean writeString(long address, String string);

  static native String readString(long address, int maxLen);

  static native void stalkerFollowMe(int eventMask, int batchEvents, Object transformer);

  static native void stalkerUnfollowMe();

  static native void stalkerFollowThread(long threadId, int eventMask, int batchEvents, Object transformer);

  static native void stalkerUnfollowThread(long threadId);

  /** 追踪窗口: 毫秒数(0 = 不限时; 未调用 = 默认 60000 兜底); 同时开启新一轮窗口 */
  static native void stalkerSetTimeout(int timeoutMs);

  /** 事件数上限(0 = 不限); 抓到该数量事件后自动停止(软上限, 5ms 轮询) */
  static native void stalkerSetMaxEvents(int maxEvents);

  /** 停止原因: 0 无 / 1 超时 / 2 事件数上限 */
  static native int stalkerGetStopReason();

  /** 本次窗口内已投递的事件数 */
  static native long stalkerGetEventCount();

  static native long stalkerFollowFunction(long address, int eventMask, int batchEvents, Object transformer);

  static native boolean stalkerUnfollowFunction(long handle);

  /** stalkerTraceFunction 的 flags bit0: 只记录主线程 */
  public static final int TRACE_FLAG_MAIN_THREAD_ONLY = 0x1;
  /** stalkerTraceFunction 的 flags bit1: 不记录访存指令读/写的数据(默认记录) */
  public static final int TRACE_FLAG_MEM_BYTES_OFF = 0x2;
  /**
   * stalkerTraceFunction 的 flags bit2: 目标库未加载时先登记(状态 PENDING), 库被
   * dlopen 加载后自动解析并开始(安装发生在 dlopen 返回其调用方之前, 首次调用即被
   * 记录)。等待无限期, stalkerTraceStop() 取消; 库已加载且可解析时与不传本 flag
   * 等价, 立即开始。等待中换目标同样被拒(进程内单会话)。
   */
  public static final int TRACE_FLAG_DELAYED = 0x4;

  /**
   * 内置默认 trace(寄存器级记录 + 访存字节, 结束自动落盘): 按 address 直接追踪, 或
   * lib+symbol 解析后追踪。timeoutMs &lt;0 用默认 60s 兜底、0 不限时; maxEvents 0 = 不限;
   * flags bit0 = 只记录主线程(TRACE_FLAG_MAIN_THREAD_ONLY); bit1 = 不记录访存字节
   * (TRACE_FLAG_MEM_BYTES_OFF); bit2 = 延迟登记(TRACE_FLAG_DELAYED, 目标库未加载时
   * 等加载后自动开始); logPath 为落盘文件绝对路径(空串 = 不落盘, 只能 dump 取)。
   * 进程内单会话: 换目标返回 false, 同目标重复调用为幂等/重开窗口(参数以本次调用为准)。
   * 与 native/CModule 的 AlbStalkerTrace*(include/albatross.h)共用同一份实现与会话槽。
   *
   * <p>返回 true = 已开始或已登记等待(TRACE_FLAG_DELAYED); false = 失败(解析不到/
   * 换目标/装不上加载触发器)。
   *
   * <p>访存字节: 访存指令在其 EXEC 行下多打一行 mem —— 读侧是访问地址处读到的数据,
   * store 是从源寄存器/立即数重建的写入数据(W* = 源不可得, 显示写前快照), 宽度按
   * 访问宽度、上限 64 字节; 不需要这笔开销时传 TRACE_FLAG_MEM_BYTES_OFF。
   */
  public static native boolean stalkerTraceFunction(String lib, String symbol, long address,
                                                    int timeoutMs, int maxEvents, int flags, String logPath);

  /** 停止默认 trace 并落盘(含取消延迟登记); 未启动返回 false */
  public static native boolean stalkerTraceStop();

  /**
   * 默认 trace 状态(JSON): running/started/pending/state/target/log_path/stop_reason/
   * stop_reason_name/timeout_ms/max_events/engine_events/appended/dropped/
   * readable/ring_cells/mem_bytes/file_written; 延迟等待中另有 lib/symbol/offset
   * (state: 0=无会话 1=等待加载 2=进行中 3=已停止 4=延迟启动失败)。已停未落盘时
   * 本查询会补写文件。
   */
  public static native String stalkerTraceStatus();

  /** 格式化 trace 文本(追踪结束后的运行过程; maxBytes&gt;0 时截断), 未启动返回空串 */
  public static native String stalkerTraceDump(int maxBytes);

  static native void stalkerExcludeRange(long base, long size);

  static native void stalkerExcludeModule(String moduleName);

  static native void stalkerExcludeNamed(String nameSubstring);

  static native void stalkerIncludeRange(long base, long size);

  static native void stalkerIncludeModule(String moduleName);

  static native void stalkerClearInclusions();

  static native void stalkerClearExclusions();

  static native void stalkerInvalidate(long address);

  static native boolean stalkerGarbageCollect();

  static native void stalkerSetTrustThreshold(int threshold);

  static native void stalkerActivate(long target);

  static native void stalkerDeactivate();

  static native long[] stalkerPollEvents();

  static native void stalkerFlushEvents();

  static native void stalkerStopSession();

  static native boolean stalkerIteratorNext(long iterator);

  static native long stalkerIteratorAddress(long iterator);

  static native int stalkerIteratorSize(long iterator);

  static native String stalkerIteratorMnemonic(long iterator);

  static native String stalkerIteratorOperands(long iterator);

  static native void stalkerIteratorKeep(long iterator);

  static native void stalkerIteratorPutCallout(long iterator, Object callout);

  static native long cpuGetRegister(long cpuContext, String reg);

  static native void cpuSetArgument(long cpuContext, int nth, long value);

  static native long cpuGetArgument(long cpuContext, int nth);

  static native void cpuSetRegister(long cpuContext, String reg, long value);

  static void stalkerTransformCallback(long iterator, Object transformer) {
    StalkerIteratorImpl impl = new StalkerIteratorImpl(iterator);
    try {
      ((StalkerTransformer) transformer).transform(impl);
    } finally {
      impl.ensureTransformed();
    }
  }

  static void stalkerCalloutCallback(long cpuContext, Object callout) {
    ((StalkerCallout) callout).onCall(new NativeCpuContext(cpuContext));
  }


  static void enterCallback(long invokeContext, InstructionCallback callback, Object userdata, int flags) {
    callback.onCall(new NativeInvokeContext(invokeContext, flags), userdata);
  }

  static void leaveCallback(long invokeContext, InstructionCallback callback, Object userdata, int flags) {
    callback.onCall(new NativeInvokeContext(invokeContext, flags), userdata);
  }


  public static void registerLibraryCallback(SearchCallback callback, String libName) {
    registerLibraryCallbackNative(callback, libName);
  }

  enum HookAction {
    HOOK_METHOD(0), BACKUP_METHOD(1), HOOK_CONSTRUCTOR(2), BACKUP_CONSTRUCTOR(3);
    final int v;

    HookAction(int v) {
      this.v = v;
    }
  }

  public static void enumerateModules(SearchCallback callback) {
    iterLib(callback);
  }


  public static DlInfo openLib(String libName) {
    long handle = dlopen(libName);
    // 成功时 handle 是内存指针（可能带 tag ⇒ 作为 long 是负数），失败才是 0 或小 errno：
    // 不能用符号判断，否则高地址/tag 句柄在真机上会被误判成失败。
    if (handle > -4096 && handle < 4096)
      return null;
    return new DlInfo(handle);
  }

  static {
    try {
      Albatross.registerAlbNative(AlbNative.class, SearchCallback.class.getDeclaredMethod("match", String.class, long.class, long.class, int.class),
          AlbNative.class.getDeclaredMethod("enterCallback", long.class, InstructionCallback.class, Object.class, int.class),
          AlbNative.class.getDeclaredMethod("leaveCallback", long.class, InstructionCallback.class, Object.class, int.class)
      );
    } catch (Exception e) {
      Albatross.log("register AlbaNative fail", e);
    }
  }

  /**
   * 指令级插桩 hook（按库名 + 符号名）：onEnter 进入时回调，onLeave 返回前回调。
   *
   * <p><b>onEnter / onLeave 都可传 null</b>（两者不能同时为 null）。<b>不需要 leave 就传
   * null</b>：只有注册了 onLeave，派发层才会去种"改写入参返回地址"的 leave 陷阱 ——
   * 该陷阱只在**函数入口**站点上能闭合；按"符号 + 偏移"挂函数中段（观测最常用的用法）
   * 时它是纯开销：条目会只增不减、并与别处入口站点的 leave 错账（新版框架会按站点
   * 形态自动降级为 enter-only 并告警一行，但仍是无谓的注册）。<b>注意"空实现"≠
   * null</b>：`(ctx, ud) -> {}` 同样会注册 leave 监听器、照样把陷阱装上。
   */
  public static HookRecord hookInstruction(String lib, String function, InstructionCallback onEnter, InstructionCallback onLeave, Object userdata) {
    long address = dlopen("lib" + lib + ".so");
    if (address == 0)
      return null;
    long symbolAddress = dlsym(address, function);
    dlclose(address);
    if (symbolAddress == 0)
      return null;
    return hookInstruction(symbolAddress, onEnter, onLeave, userdata);
  }


  /**
   * 延迟插桩 hook：目标库/符号尚未加载时先登记，库被加载后自动解析并安装
   * （安装早于 dlopen 返回调用方）；注册时库已加载则立即安装。
   *
   * <p>库名沿用 hookInstruction 的约定（短名，内部按 "lib"+lib+".so" 对进程 maps
   * 做子串匹配）；onApplied 可空，安装成功/失败都会回调一次，失败时
   * {@link DelayedHookRecord#getState()} 为 FAILED。只生效一次：目标 dlclose 后
   * 重新加载不会自动重挂。
   *
   * <p><b>onEnter / onLeave 都可传 null，不需要 leave 就传 null</b>（语义见
   * {@link #hookInstruction(String, String, InstructionCallback, InstructionCallback, Object)}）——
   * 按偏移的观测（{@code hookInstructionDelayed(lib, offset, ...)}）尤其要这样：中段站点上
   * 注册 leave 只会让框架去种一个永远到不了的陷阱。
   */
  public static DelayedHookRecord hookInstructionDelayed(String lib, String function,
                                                         InstructionCallback onEnter, InstructionCallback onLeave, Object userdata) {
    return hookInstructionDelayed(lib, function, onEnter, onLeave, userdata, null);
  }

  public static DelayedHookRecord hookInstructionDelayed(String lib, String function,
                                                         InstructionCallback onEnter, InstructionCallback onLeave, Object userdata,
                                                         DelayedHookApplied onApplied) {
    return registerDelayedHook(lib, function, 0, onEnter, onLeave, userdata, onApplied);
  }

  /**
   * 按偏移的延迟插桩 hook：目标符号拿不到（未导出/只有 ID 里的地址）时用。
   * offset 为 ELF 虚拟地址（IDA/Ghidra/readelf 里看到的 vaddr），实际挂钩地址 =
   * 模块基址 + offset；库已加载时可先用 DlInfo 拿符号地址再减模块基址换算出 offset。
   * 其余语义（安装时机/状态/取消/回调）与按符号名的重载一致。
   */
  public static DelayedHookRecord hookInstructionDelayed(String lib, long offset,
                                                         InstructionCallback onEnter, InstructionCallback onLeave, Object userdata) {
    return hookInstructionDelayed(lib, offset, onEnter, onLeave, userdata, null);
  }

  public static DelayedHookRecord hookInstructionDelayed(String lib, long offset,
                                                         InstructionCallback onEnter, InstructionCallback onLeave, Object userdata,
                                                         DelayedHookApplied onApplied) {
    return registerDelayedHook(lib, null, offset, onEnter, onLeave, userdata, onApplied);
  }

  private static DelayedHookRecord registerDelayedHook(String lib, String symbol, long offset,
                                                       InstructionCallback onEnter, InstructionCallback onLeave, Object userdata,
                                                       DelayedHookApplied onApplied) {
    if (lib == null || (onEnter == null && onLeave == null))
      return null;
    if (symbol == null && offset == 0)
      return null;
    DelayedHookRecord record = new DelayedHookRecord(onApplied);
    long handle = hookInstrumentDelayedNative("lib" + lib + ".so", symbol, offset, onEnter,
        onLeave, userdata, record);
    // 成功时返回堆上 bridge 指针（带 tag ⇒ 作为 long 可能是负数），失败只有
    // ALB_DELAYED_ERR_ARG/MEMORY/TRIGGER = -1/-2/-3 这种小错误码 ⇒ 按"小整数"判失败。
    // 用 handle < 0 判会把真机上的高地址句柄误判成失败（模拟器指针高位为 0 所以一直没暴露）。
    if (handle > -4096 && handle < 4096) {
      Albatross.log("hookInstructionDelayed fail: " + lib
          + (symbol != null ? "!" + symbol : "+0x" + Long.toHexString(offset)) + " err=" + handle);
      return null;
    }
    record.nativePtr = handle;
    long id = delayedHookIdNative(handle);
    if (id != 0)
      record.id = id;
    return record;
  }

  /**
   * 声明式延迟 hook：库尚未加载时先挂一个一次性加载回调，库被加载后按注解
   * （@TargetLibrary/@FuncHook/@FuncBackup/@Symbol）安装，安装路径与
   * {@link #hookNative(Class, String)} 相同。
   */
  public static boolean hookNativeDelayed(Class<?> hooker, String lib) {
    if (hooker == null)
      return false;
    final String[] candidates;
    if (lib != null) {
      candidates = new String[]{lib};
    } else {
      TargetLibrary targetClassAnno = hooker.getAnnotation(TargetLibrary.class);
      if (targetClassAnno == null)
        return false;
      candidates = targetClassAnno.value();
    }
    if (candidates.length == 0)
      return false;
    SearchCallback loadCallback = (symbol, addr, size, idx) -> {
      for (String name : candidates) {
        if (name == null)
          continue;
        long handle = dlopen("lib" + name + ".so");
        if (handle == 0)
          continue;
        dlclose(handle);
        // 目标库已加载: 安装全部 @Symbol 并注销本回调(返回 false)
        hookNative(hooker, name);
        return false;
      }
      return true;
    };
    registerLibraryCallback(loadCallback, null);
    return true;
  }

  public static HookRecord hookInstruction(long symbolAddress, InstructionCallback onEnter, InstructionCallback onLeave, Object userdata) {
    if (symbolAddress == 0)
      return null;
    long data = hookInstructionNative(symbolAddress, onEnter, onLeave, userdata);
    if (data == 0) {
      return null;
    }
    HookRecord hookRecord = new HookRecord();
    hookRecord.nativePtr = data;
    return hookRecord;
  }

  public static int hookNative() {
    return hookNative(Albatross.getCallerClass(), null);
  }

  public static int hookNative(Class<?> hooker) {
    return hookNative(hooker, null);
  }

  public static int hookNative(Class<?> hooker, String lib) {
    long address = 0;
    if (lib == null) {
      TargetLibrary targetClassAnno = hooker.getAnnotation(TargetLibrary.class);
      if (targetClassAnno == null)
        return 0;
      String[] libs = targetClassAnno.value();
      for (String name : libs) {
        address = dlopen("lib" + name + ".so");
        if (address != 0)
          break;
      }
    } else {
      address = dlopen("lib" + lib + ".so");
    }
    if (address == 0) {
      Albatross.log("find lib fail:" + hooker);
      return 0;
    }
    int successCount = 0;
    Field[] fields = hooker.getDeclaredFields();
    for (Field field : fields) {
      boolean isStatic = Modifier.isStatic(field.getModifiers());
      if (!isStatic) {
        continue;
      }
      Class<?> fieldType = field.getType();
      if (fieldType != long.class)
        continue;
      Symbol symbol = field.getAnnotation(Symbol.class);
      if (symbol == null)
        continue;
      long symbolAddress = 0;
      String[] symbolNames = symbol.value();
      if (symbolNames.length > 0) {
        for (String s : symbolNames) {
          symbolAddress = dlsym(address, s);
          if (symbolAddress != 0)
            break;
        }
      } else {
        symbolAddress = dlsym(address, field.getName());
      }
      if (symbolAddress == 0)
        continue;
      field.setAccessible(true);
      try {
        field.set(null, symbolAddress);
        successCount += 1;
        Albatross.log("find symbol " + field.getName() + " address:" + symbolAddress);
      } catch (IllegalAccessException ignore) {
      }
    }
    FuncBackup funcBackup;
    FuncHook funcHook;
    for (Method m : hooker.getDeclaredMethods()) {
      Annotation[] annotations = m.getAnnotations();
      if (annotations.length == 0)
        continue;
      int modifiers = m.getModifiers();
      if (!Modifier.isStatic(modifiers))
        continue;
      HookAction hookWay;
      String[] names = null;
      if ((funcBackup = m.getAnnotation(FuncBackup.class)) != null) {
        hookWay = HookAction.BACKUP_METHOD;
        names = funcBackup.value();
        if (!Modifier.isNative(modifiers)) {
          Albatross.log("backup method " + m + " must native");
          continue;
        }
      } else if ((funcHook = m.getAnnotation(FuncHook.class)) != null) {
        hookWay = HookAction.HOOK_METHOD;
        names = funcHook.value();
      }  else {
        continue;
      }
      long targetAddress = 0;
      if (names == null || names.length == 0) {
        // 命名约定 <符号>$Backup / <符号>$Hook, 与 Albatross.getSplitValue 一致
        targetAddress = dlsym(address, getSplitValue(m.getName()));
      } else {
        for (String n : names) {
          targetAddress = dlsym(address, n);
          if (targetAddress != 0)
            break;
        }
      }
      if (targetAddress == 0) {
        Albatross.log("can not find address for:" + m);
        continue;
      }
      switch (hookWay) {
        case BACKUP_METHOD: {
          NativeMethodRecord record = NativeMethodParser.parseMethod(m);
          if (record == null) {
            Albatross.log("backup method " + m + " fail,wrong signatrue");
          } else {
            long result = AlbNative.backupMethodNative(targetAddress, m, record.args, record.retType);
            if (result != 0) {
              Albatross.log("backup method " + m + " success");
              successCount += 1;
            } else {
              Albatross.log("backup method " + m + " fail");
            }
          }
          break;
        }
        case HOOK_METHOD: {
          NativeMethodRecord record = NativeMethodParser.parseMethod(m);
          if (record == null) {
            Albatross.log("hook method " + m + " fail,wrong signatrue");
          } else if (checkFuncHookSignature(m, record)) {
            long result = hookFuncNative(targetAddress, m, record.args, record.retType);
            if (result != 0) {
              Albatross.log("hook method " + m + " success");
              funcHookHandles.put(m, result);
              successCount += 1;
            } else {
              Albatross.log("hook method " + m + " fail");
            }
          }
          break;
        }
      }
    }
    dlclose(address);
    return successCount;
  }

  public static int unhookNative(Class<?> hooker) {
    return unhookNative(hooker, null);
  }

  public static int unhookNative(Class<?> hooker, String lib) {
    long address = 0;
    if (lib == null) {
      TargetLibrary targetClassAnno = hooker.getAnnotation(TargetLibrary.class);
      if (targetClassAnno == null)
        return 0;
      String[] libs = targetClassAnno.value();
      for (String name : libs) {
        address = dlopen("lib" + name + ".so");
        if (address != 0)
          break;
      }
    } else {
      address = dlopen("lib" + lib + ".so");
    }
    if (address == 0) {
      Albatross.log("find lib fail:" + hooker);
      return 0;
    }
    int successCount = 0;
    Field[] fields = hooker.getDeclaredFields();
    for (Field field : fields) {
      boolean isStatic = Modifier.isStatic(field.getModifiers());
      if (!isStatic) {
        continue;
      }
      Class<?> fieldType = field.getType();
      if (fieldType != long.class)
        continue;
      Symbol symbol = field.getAnnotation(Symbol.class);
      if (symbol == null)
        continue;
      field.setAccessible(true);
      try {
        if (field.getType() == long.class) {
          field.setLong(null, 0);
          successCount += 1;
          Albatross.log("unset symbol " + field.getName());
        }
      } catch (IllegalAccessException ignore) {
      }
    }
    FuncBackup funcBackup;
    FuncHook funcHook;
    for (Method m : hooker.getDeclaredMethods()) {
      Annotation[] annotations = m.getAnnotations();
      if (annotations.length == 0)
        continue;
      int modifiers = m.getModifiers();
      if (!Modifier.isStatic(modifiers))
        continue;
      HookAction hookWay;
      String[] names = null;
      if ((funcBackup = m.getAnnotation(FuncBackup.class)) != null) {
        hookWay = HookAction.BACKUP_METHOD;
        names = funcBackup.value();
        if (!Modifier.isNative(modifiers)) {
          Albatross.log("backup method " + m + " must native");
          continue;
        }
      } else if ((funcHook = m.getAnnotation(FuncHook.class)) != null) {
        hookWay = HookAction.HOOK_METHOD;
        names = funcHook.value();
      } else {
        continue;
      }
      long targetAddress = 0;
      if (names == null || names.length == 0) {
        // 命名约定 <符号>$Backup / <符号>$Hook, 与 Albatross.getSplitValue 一致
        targetAddress = dlsym(address, getSplitValue(m.getName()));
      } else {
        for (String n : names) {
          targetAddress = dlsym(address, n);
          if (targetAddress != 0)
            break;
        }
      }
      if (targetAddress == 0) {
        Albatross.log("can not find address for:" + m);
        continue;
      }
      switch (hookWay) {
        case BACKUP_METHOD: {
          NativeMethodRecord record = NativeMethodParser.parseMethod(m);
          if (record == null) {
            Albatross.log("backup method " + m + " fail,wrong signatrue");
          } else {
            if (unBackupMethodNative(m)) {
              Albatross.log("unbackup " + Albatross.methodToString(m) + " success");
              successCount += 1;
            }
          }
          break;
        }
        case HOOK_METHOD: {
          if (funcHookHandles.containsKey(m)) {
            if (unHookFunc(m)) {
              Albatross.log("unhook " + Albatross.methodToString(m) + " success");
              successCount += 1;
            }
          }
          break;
        }
      }
    }
    dlclose(address);
    return successCount;
  }


  private static String getSplitValue(String name) {
    int index = name.indexOf('$');
    return index > 0 ? name.substring(0, index) : name;
  }

  // FuncHook 句柄表: Method 按底层方法等值比较, 反射新取的 Method 也能命中;
  // 卸载失败(句柄仍在表里)可重试
  private static final Map<Method, Long> funcHookHandles = new ConcurrentHashMap<>();

  public static boolean unHookFunc(Method method) {
    if (method == null)
      return false;
    Long handle = funcHookHandles.get(method);
    if (handle == null)
      return false;
    if (!unHookFuncNative(handle))
      return false;
    funcHookHandles.remove(method);
    return true;
  }

  public static boolean unHookFunc(Class<?> hooker, String name) {
    if (hooker == null || name == null)
      return false;
    for (Method m : hooker.getDeclaredMethods()) {
      if (name.equals(m.getName()) && funcHookHandles.containsKey(m))
        return unHookFunc(m);
    }
    return false;
  }

  // FuncHook 走 InvocationContext 编组参数: 拦截器上下文无浮点寄存器读取,
  // byte[] 也无法从裸指针反推长度, 这几类签名安装期直接拒绝
  private static boolean checkFuncHookSignature(Method m, NativeMethodRecord record) {
    if (record.args != null && record.args.length > 16) {
      Albatross.log("hook method " + m + " fail,to many args");
      return false;
    }
    if (record.args != null) {
      for (byte a : record.args) {
        if (a == ARG_FLOAT || a == ARG_DOUBLE || a == ARG_BYTES) {
          Albatross.log("hook method " + m + " fail,float/double/bytes arg unsupported");
          return false;
        }
      }
    }
    byte ret = record.retType;
    if (ret == ARG_FLOAT || ret == ARG_DOUBLE || ret == ARG_BYTES) {
      Albatross.log("hook method " + m + " fail,float/double/bytes return unsupported");
      return false;
    }
    return true;
  }

}
