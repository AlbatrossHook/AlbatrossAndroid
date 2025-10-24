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

import static qing.albatross.annotation.CallWay.CURRENT;
import static qing.albatross.annotation.ExecOption.*;
import static qing.albatross.core.InstructionListener.hookInstructionNative;
import static qing.albatross.reflection.ReflectUtils.getArgumentTypesFromString;

import android.annotation.SuppressLint;
import android.app.Application;
import android.app.Instrumentation;
import android.content.Context;
import android.os.Build;
import android.os.Debug;
import android.os.Handler;
import android.util.ArrayMap;
import android.util.ArraySet;
import android.util.Log;

import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.ConcurrentModificationException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import dalvik.system.BaseDexClassLoader;
import qing.albatross.annotation.Alias;
import qing.albatross.annotation.ArgumentTypeSlot;
import qing.albatross.annotation.ByName;
import qing.albatross.annotation.ConstructorBackup;
import qing.albatross.annotation.ConstructorHook;
import qing.albatross.annotation.ConstructorHookBackup;
import qing.albatross.annotation.DefOption;
import qing.albatross.annotation.FieldRef;
import qing.albatross.annotation.MethodBackup;
import qing.albatross.annotation.MethodHook;
import qing.albatross.annotation.MethodHookBackup;
import qing.albatross.annotation.ParamInfo;
import qing.albatross.annotation.RunMode;
import qing.albatross.annotation.StaticMethodBackup;
import qing.albatross.annotation.StaticMethodHook;
import qing.albatross.annotation.StaticMethodHookBackup;
import qing.albatross.annotation.FuzzyMatch;
import qing.albatross.annotation.TargetClass;
import qing.albatross.common.HookResult;
import qing.albatross.exception.AlbatrossErr;
import qing.albatross.exception.AlbatrossException;
import qing.albatross.exception.CheckParameterTypesResult;
import qing.albatross.exception.FieldException;
import qing.albatross.exception.FieldExceptionReason;
import qing.albatross.exception.FindMethodException;
import qing.albatross.exception.HookInterfaceErr;
import qing.albatross.exception.HookerStructErr;
import qing.albatross.exception.MethodException;
import qing.albatross.exception.MethodExceptionReason;
import qing.albatross.exception.MirrorExtendErr;
import qing.albatross.exception.NotNativeBackupErr;
import qing.albatross.exception.RedundantFieldErr;
import qing.albatross.exception.RedundantMethodErr;
import qing.albatross.exception.RepetitiveBackupErr;
import qing.albatross.exception.RequiredClassErr;
import qing.albatross.exception.RequiredFieldErr;
import qing.albatross.exception.RequiredInstanceErr;
import qing.albatross.exception.RequiredMethodErr;
import qing.albatross.exception.VirtualCallBackupErr;
import qing.albatross.reflection.BooleanFieldDef;
import qing.albatross.reflection.BooleanMethodDef;
import qing.albatross.reflection.ByteFieldDef;
import qing.albatross.reflection.CharFieldDef;
import qing.albatross.reflection.ConstructorDef;
import qing.albatross.reflection.DoubleFieldDef;
import qing.albatross.reflection.FieldDef;
import qing.albatross.reflection.FloatFieldDef;
import qing.albatross.reflection.IntFieldDef;
import qing.albatross.reflection.LongFieldDef;
import qing.albatross.reflection.MethodDef;
import qing.albatross.reflection.ReflectUtils;
import qing.albatross.reflection.ReflectionBase;
import qing.albatross.reflection.ShortFieldDef;
import qing.albatross.reflection.StaticBoolFieldDef;
import qing.albatross.reflection.StaticByteFieldDef;
import qing.albatross.reflection.StaticCharFieldDef;
import qing.albatross.reflection.StaticDoubleFieldDef;
import qing.albatross.reflection.StaticFieldDef;
import qing.albatross.reflection.StaticFloatFieldDef;
import qing.albatross.reflection.StaticIntConstFieldDef;
import qing.albatross.reflection.StaticIntFieldDef;
import qing.albatross.reflection.StaticLongFieldDef;
import qing.albatross.reflection.StaticMethodDef;
import qing.albatross.reflection.StaticShortFieldDef;
import qing.albatross.reflection.StaticVoidMethodDef;
import qing.albatross.reflection.VoidMethodDef;

public final class Albatross {

  private Albatross() {
  }

  static class _$ {
    public static int s1;
    public static int s2;
    static int s3;
    Object i1;
    int i2;
    char i3;
    long i4;
    float i5;

    native void iMethod1();

    native void iMethod2();

    native void iMethod3();

    native void iMethod4();

    private static final native int sMethod5();

    private static final native int sMethod6();

    static native void iMethod7();
  }

  static class _$2 {
    static int s1;
    Object i1;
    int i2;
    char i3;

    native void iMethod4();

    private static final native int sMethod5();

    private static final native int sMethod6();

    static native void iMethod7();
  }

  private enum InitResultFlag {
    INIT_OK(1),
    BACKUP_CALL_JIT(2),
    REPLACE_ENSURE_CLASS_INIT(4),
    AOT_SUPPORTED(8),
    INS_ENABLE(0x10),
    JIT_SUPPORTED(0x20);

    private final int mask;

    InitResultFlag(int mask) {
      this.mask = mask;
    }

    public boolean isSet(int initResult) {
      return (initResult & mask) != 0;
    }
  }


  // String constants
  public static final String HOOK_SUFFIX = "$Hook";
  public static final String BACKUP_SUFFIX = "$Backup";
  public static final String TAG = Albatross.class.getSimpleName();
  public static final String ALBATROSS_INIT_FLAGS_KEY = "albatross_init_flags";

  // Status constants
  public static final int STATUS_INIT_OK = 1;
  public static final int STATUS_DISABLED = 2;
  public static final int STATUS_NOT_INIT = 4;
  public static final int STATUS_INIT_FAIL = 8;


  // Special return values
  public static final int CLASS_ALREADY_HOOK = -1000000000;
  public static final int REDUNDANT_ELEMENT = -1000000001;

  // Search flags
  public static final int SEARCH_STATIC = 1;
  public static final int SEARCH_INSTANCE = 2;
  public static final int SEARCH_ALL = SEARCH_STATIC | SEARCH_INSTANCE;


  //note:these two fields must be placed right next to each other!
  public static int initStatus = STATUS_NOT_INIT;
  public static boolean initClass = false;

  public static boolean loaderFromCaller = false;

  /**
   * Gets the target class for a hooker class based on its TargetClass annotation.
   *
   * @param hooker the hooker class to get target class for
   * @return the target class, or null if no TargetClass annotation is present
   */
  public static Class<?> getHookerTargetClass(Class<?> hooker) {
    TargetClass targetClass = hooker.getAnnotation(TargetClass.class);
    if (targetClass != null) {
      return getTargetClassFromAnnotation(targetClass, null);
    }
    return null;
  }

  private static boolean checkTargetClass(Set<Class<?>> dependencies, Class<?> hooker, Class<?> expectClass) throws AlbatrossErr {
    if (expectClass != null) {
      while (hooker.isArray() && expectClass.isArray()) {
        hooker = hooker.getComponentType();
        expectClass = expectClass.getComponentType();
      }
    }
    TargetClass targetClass = hooker.getAnnotation(TargetClass.class);
    if (targetClass != null) {
      Class<?> t = getTargetClassFromAnnotation(targetClass, expectClass.getClassLoader());
      if (t == null || t == TargetClass.class) {
        t = expectClass;
      } else {
        if (!t.isAssignableFrom(expectClass)) {
          return false;
        }
        if (t.isInterface())
          t = expectClass;
      }
      if (dependencies != null) {
        if (addAssignableHooker(hooker, t)) {
          dependencies.add(hooker);
        }
      }
      return true;
    }
    return false;
  }

  @Alias("checkMethodReturn")
  private static Set<Class<?>> checkMethodReturn(Set<Class<?>> dependencies, Object original, Method replacement) throws AlbatrossException {
    Class<?> returnType = replacement.getReturnType();
    if (original instanceof Method) {
      Class<?> originalReturn = ((Method) original).getReturnType();
      if (!returnType.isAssignableFrom(originalReturn)) {
        FuzzyMatch fuzzyMatch = replacement.getAnnotation(FuzzyMatch.class);
        if (fuzzyMatch != null) {
          if (originalReturn.isPrimitive()) {
            if (ReflectUtils.isPrimMatch(returnType, originalReturn))
              return dependencies;
            throw new MethodException(replacement, MethodExceptionReason.WRONG_RETURN, "Incompatible return types. " + ((Method) original).getName() + ": " + originalReturn + ", " + replacement.getName() + ": " + returnType);
          }
        }
        if (!checkTargetClass(dependencies, returnType, originalReturn))
          throw new MethodException(replacement, MethodExceptionReason.WRONG_RETURN, "Incompatible return types. " + ((Method) original).getName() + ": " + originalReturn + ", " + replacement.getName() + ": " + returnType);
      }
    } else if (original instanceof Constructor) {
      if (!returnType.equals(void.class)) {
        throw new MethodException(replacement, MethodExceptionReason.WRONG_RETURN, "Incompatible return types. " + "<init>" + ": " + "V" + ", " + replacement.getName() + ": " + returnType);
      }
    }
    return dependencies;
  }

  // Architecture constants
  public static final int kArm = 1;
  public static final int kArm64 = 2;
  public static final int kX86 = 3;
  public static final int kX86_64 = 4;

  public static native int getRuntimeISA();

  public native static boolean initRpcClass(Class<?> clz);

  public static native boolean registerOceanTracker(Class<?> ocean);

  public static boolean addAssignableHooker(Class<?> hooker, Class<?> targetClass) throws AlbatrossErr {
    int setResult = setHookerAssignableNative(hooker, targetClass);
    if (setResult > 0) {
      hookClassInternal(hooker, null, targetClass, null);
      return true;
    }
    return false;
  }

  private static Set<Class<?>> checkCompatibleMethods(Set<Class<?>> dependencies, Member original, Method replacement, String replacementName) throws AlbatrossException {
    ArrayList<Class<?>> originalParams;
    if (original instanceof Method) {
      Method originalMethod = (Method) original;
      originalParams = new ArrayList<>(Arrays.asList((originalMethod).getParameterTypes()));
      if (!Modifier.isStatic(originalMethod.getModifiers())) {
        originalParams.add(0, originalMethod.getDeclaringClass());
      }
    } else if (original instanceof Constructor) {
      Constructor<?> constructor = (Constructor<?>) original;
      originalParams = new ArrayList<>(Arrays.asList(constructor.getParameterTypes()));
      originalParams.add(0, constructor.getDeclaringClass());
    } else {
      throw new IllegalArgumentException("Type of target method is wrong");
    }
    ArrayList<Class<?>> replacementParams = new ArrayList<>(Arrays.asList(replacement.getParameterTypes()));
    boolean isReplaceStatic = Modifier.isStatic(replacement.getModifiers());
    if (!isReplaceStatic) {
      replacementParams.add(0, replacement.getDeclaringClass());
    }
    if (originalParams.size() != replacementParams.size()) {
      throw new MethodException(replacement, MethodExceptionReason.ARGUMENT_SIZE_NOT_MATCH, "Number of arguments don't match. original: " + originalParams.size() + ", " + replacementName + ": " + replacementParams.size());
    }
    for (int i = 0; i < originalParams.size(); i++) {
      Class<?> expectClass = replacementParams.get(i);
      Class<?> originalClz = originalParams.get(i);
      if (!expectClass.isAssignableFrom(originalClz)) {
        if (!checkTargetClass(dependencies, expectClass, originalClz))
          throw new MethodException(replacement, MethodExceptionReason.WRONG_ARGUMENT, "Incompatible argument #" + i + ":: " + originalClz + ", " + replacementName + ": " + expectClass);
      }
    }
    return dependencies;
  }

  /**
   * Replaces a target method with a hook method without creating a backup.
   *
   * @param target the target method to replace
   * @param hook   the hook method to replace with
   * @return true if the replacement was successful
   * @throws AlbatrossException if the replacement fails
   */
  public static boolean replace(Member target, Method hook) throws AlbatrossException {
    return backupAndHook(target, hook, null, true, true, null, DO_NOTHING, DO_NOTHING);
  }

  /**
   * Backs up a target method and replaces it with a hook method.
   *
   * @param target the target method to backup and replace
   * @param hook   the hook method to replace with
   * @param backup the backup method to store the original implementation
   * @return true if the backup and hook was successful
   * @throws AlbatrossException if the operation fails
   */
  public static boolean backupAndHook(Member target, Method hook, Method backup) throws AlbatrossException {
    return backupAndHook(target, hook, backup, true, true, null, DO_NOTHING, DO_NOTHING);
  }

  private static int hookDependency(Set<Class<?>> dependencies) throws HookerStructErr {
    return 0;
  }

  @Alias("backupAndHook")
  public static boolean backupAndHook(Member target, Method hook, Method backup, boolean check, boolean checkReturn, Set<Class<?>> dependencies, int targetExecMode, int hookerExecMode) throws AlbatrossException {
    if (initStatus > STATUS_INIT_OK)
      return false;
    if (target == null) {
      throw new IllegalArgumentException("null target method");
    }
    if (hook == null) {
      throw new IllegalArgumentException("null hook method");
    }
    Set<Class<?>> currentDependencies;
    if (dependencies == null) {
      currentDependencies = new HashSet<>();
    } else {
      currentDependencies = dependencies;
    }
    if (backup != null) {
      int modifiers = backup.getModifiers();
      if ((modifiers & Modifier.STATIC) == 0) {
        if ((modifiers & (Modifier.FINAL | Modifier.PRIVATE)) == 0)
          throw new VirtualCallBackupErr(backup);
      }
      if (hook != backup) {
        if ((modifiers & Modifier.NATIVE) == 0) {
          throw new NotNativeBackupErr(backup);
        }
        if (checkReturn)
          checkMethodReturn(currentDependencies, target, backup);
        if (check)
          checkCompatibleMethods(currentDependencies, target, backup, "Backup");
      }
    }
    if (checkReturn)
      checkMethodReturn(currentDependencies, target, hook);
    if (check)
      checkCompatibleMethods(null, target, hook, "Hook");
    int result = backupAndHookNative(target, hook, backup, targetExecMode, hookerExecMode);
    if (result == HookResult.HOOK_SUCCESS) {
      return true;
    }
    if (result == HookResult.HOOK_FAIL) {
      throw new RuntimeException("Failed to hook " + target + " with " + hook);
    }
    log("already hook " + target + " with " + hook);
    return false;
  }

  public static boolean backup(Member target, Method backup) throws AlbatrossException {
    return backup(target, backup, true, true, null, DO_NOTHING, CURRENT);
  }

  @Alias("backup")
  public static boolean backup(Member target, Method backup, boolean check, boolean checkReturn, Set<Class<?>> dependencies, int execMode, int backupWay) throws AlbatrossException {
    if (initStatus > STATUS_INIT_OK)
      return false;
    if (target == null) {
      throw new IllegalArgumentException("null target method");
    }
    int modifiers = backup.getModifiers();
    if ((modifiers & Modifier.STATIC) == 0) {
      if ((modifiers & (Modifier.PRIVATE)) == 0)
        throw new VirtualCallBackupErr(backup);
    }
    if ((modifiers & Modifier.NATIVE) == 0) {
      throw new NotNativeBackupErr(backup);
    }
    Set<Class<?>> currentDependencies;
    if (dependencies != null) {
      currentDependencies = dependencies;
    } else {
      currentDependencies = new ArraySet<>();
    }
    if (checkReturn)
      checkMethodReturn(currentDependencies, target, backup);
    if (check)
      checkCompatibleMethods(currentDependencies, target, backup, "Backup");
    int result = backupNative(target, backup, execMode, backupWay);
    if (result == HookResult.HOOK_SUCCESS) {
      return true;
    }
    if (result == HookResult.HOOK_FAIL) {
      throw new RuntimeException("Failed to backup " + target + " to " + backup);
    }
    log("already backup " + target + " to " + backup);
    return false;
  }

  public static boolean backupField(Field target, Field backup) throws FieldException, AlbatrossErr {
    return backupField(new HashSet<>(), target, backup, null);
  }

  @Alias("backupField")
  private static boolean backupField(Set<Class<?>> dependencies, Field target, Field backup, Class<?> targetType) throws FieldException, AlbatrossErr {
    if (containsFlags(FLAG_FIELD_INVALID))
      return false;
    if (target == null) {
      throw new IllegalArgumentException("null target field");
    }
    boolean isStatic = Modifier.isStatic(backup.getModifiers());
    if (isStatic != Modifier.isStatic(target.getModifiers())) {
      throw FieldException.create(backup, target, FieldExceptionReason.WRONG_STATIC_FIELD);
    }
    if (isStatic && !containsFlags(FLAG_FIELD_BACKUP_STATIC)) {
      throw FieldException.create(backup, target, FieldExceptionReason.FIELD_BAN);
    }
    Class<?> type = backup.getType();
    if (targetType == null)
      targetType = target.getType();
    if (!type.isAssignableFrom(targetType)) {
      if (!checkTargetClass(dependencies, type, targetType))
        throw FieldException.create(backup, target, FieldExceptionReason.WRONG_TYPE);
    }
    int result = backupFieldNative(target, backup);
    if (result == HookResult.HOOK_SUCCESS)
      return true;
    if (result == HookResult.HOOK_FAIL) {
      throw new RuntimeException("Failed to backup " + target + " to " + backup);
    }
    log("already backup " + target + " to " + backup);
    return false;
  }

  static native long hookMethodNative(Member member, Object callback, int returnTYpe, int paramCount);

  static native void unHookMethodNative(long callbackId);

  private synchronized static native int unhookNative(Member target, Method hook, Method backup);

  public static boolean unhookMethod(Member target, Method hook, Method backup) {
    Albatross.transactionBegin();
    int res = unhookNative(target, hook, backup);
    boolean success = res == 0;
    Albatross.transactionEnd(success);
    return success;
  }

  private synchronized static native int backupAndHookNative(Object target, Method hook, Method backup, int targetExecMode, int hookerExecMode);


  public static native int getMethodHookCount(Member target);


  public static InstructionListener hookInstruction(Member member, int dexPc, InstructionCallback callback) {
    return hookInstruction(member, dexPc, dexPc, callback, DO_NOTHING);
  }

  public static InstructionListener hookInstruction(Member member, int minDexPc, int maxDexPc, InstructionCallback callback) {
    if (containsFlags(FLAG_NO_COMPILE) || (maxDexPc - minDexPc < 15))
      return hookInstruction(member, minDexPc, maxDexPc, callback, DO_NOTHING);
    if (maxDexPc - minDexPc > 25) {
      return hookInstruction(member, minDexPc, maxDexPc, callback, NATIVE_CODE);
    }
    return hookInstruction(member, minDexPc, maxDexPc, callback, AOT);
  }


  public static InstructionListener hookInstruction(Member member, int minDexPc, int maxDexPc, InstructionCallback callback, int compile) {
    InstructionListener listener = new InstructionListener();
    if (Modifier.isStatic(member.getModifiers())) {
      ensureClassInitialized(member.getDeclaringClass());
    }
    long listenerId = hookInstructionNative(member, minDexPc, maxDexPc, listener);
    if (listenerId > 4096 || listenerId < 0) {
      listener.listenerId = listenerId;
      listener.callback = callback;
      if (compile != DO_NOTHING) {
        compileClass(callback.getClass(), compile);
      }
      listener.member = member;
      return listener;
    }
    return null;
  }

  public static MethodCallHook hookMethod(Member member, MethodCallback callback, int compile) {
    MethodCallHook listener = new MethodCallHook(member);
    ReturnType returnType = listener.returnType;
    long listenerId = hookMethodNative(member, listener, returnType.value, listener.parameterTypes.length + listener.argOffset);
    if (listenerId > 4096 || listenerId < 0) {
      listener.listenerId = listenerId;
      listener.callback = callback;
      if (compile != DO_NOTHING) {
        compileClass(callback.getClass(), compile);
      }
      return listener;
    }
    return null;
  }


  private synchronized static native int unBackupNative(Method backup, int backupWay);

  private synchronized static native int backupNative(Object target, Method backup, int execMode, int backupWay);

  public synchronized static native int backupFieldNative(Field target, Field backup);

  public synchronized static native int unBackupHookerFieldNative(Class<?> backupClass);


  private synchronized static native int setHookerAssignableNative(Class<?> hooker, Class<?> targetClass);

  private synchronized static native boolean isHookerAssignableNative(Class<?> hooker, Class<?> targetClass);

  public synchronized static native int decompileMethod(Method method, boolean disableJit);

  private static native int compileMethodNative(Member method, int execMode);

  public static boolean isCompiled(Method method) {
    return entryPointFromQuickCompiledCode(method) != 0;
  }

  public static native long entryPointFromQuickCompiledCode(Member method);

  public static int compileClass(Class<?> clazz, int compileOption) {
    if (containsFlags(FLAG_NO_COMPILE))
      return 0;
    return compileClassNative(clazz, compileOption);
  }

  public static int compileClassByAnnotation(Class<?> clazz, int compileOption) {
    if (containsFlags(FLAG_NO_COMPILE))
      return 0;
    Method[] methods = clazz.getDeclaredMethods();
    RunMode runMode;
    int r = 0;
    for (Method method : methods) {
      if ((runMode = method.getAnnotation(RunMode.class)) != null) {
        if (setMethodExecMode(method, runMode.value()))
          r += 1;
      } else if (compileOption != DO_NOTHING) {
        if (setMethodExecMode(method, compileOption))
          r += 1;
      }
    }
    return r;
  }

  private static native int compileClassNative(Class<?> clazz, int execMode);

  public static boolean compileMethod(Member method) {
    return setMethodExecMode(method, NATIVE_CODE);
  }

  public static boolean setMethodExecMode(Member method, int execMode) {
    if (containsFlags(FLAG_NO_COMPILE))
      return false;
    int compileResult = compileMethodNative(method, execMode);
    if (compileResult == 0)
      return true;
    return false;
  }

  public static boolean loadLibrary(String library) {
    return loadLibrary(library, 0);
  }

  public static final int FLAG_INIT_CLASS = 0x1;
  public static final int FLAG_DEBUG = 0x2;
  public static final int FLAG_LOADER_FROM_CALLER = 0x4;
  public static final int FLAG_DISABLE_JIT = 0x8;
  public static final int FLAG_SUSPEND_VM = 0x10;
  public static final int FLAG_NO_COMPILE = 0x20;


  public static final int FLAG_FIELD_BACKUP_INSTANCE = 0x40;
  public static final int FLAG_FIELD_BACKUP_STATIC = 0x80;
  public static final int FLAG_FIELD_BACKUP_BAN = 0x100;
  public static final int FLAG_FIELD_DISABLED = 0x200;
  public static final int FLAG_FIELD_INVALID = FLAG_FIELD_BACKUP_BAN | FLAG_FIELD_DISABLED;

  public static final int FLAG_DISABLE_LOG = 0x400;
  public static final int FLAG_INJECT = 0x800;
  public static final int FLAG_INIT_RPC = 0x1000;
  public static final int FLAG_CALL_CHAIN = 0x2000;

  private static int albatross_flags = 0;

  public static boolean loadLibrary(String library, int loadFlags) {
    if ((initStatus & STATUS_INIT_OK) != STATUS_INIT_OK) {
      if ((loadFlags & FLAG_DEBUG) != 0) {
        Debug.waitForDebugger();
      }
      try {
        getRuntimeISA();
      } catch (Throwable e) {
        System.setProperty(ALBATROSS_INIT_FLAGS_KEY, loadFlags + "");
        System.loadLibrary(library);
        getRuntimeISA();
      }
      return init(loadFlags);
    }
    return true;
  }

  private native static boolean ensureClassInitializedNative(Class<?> clz);

  private native static void banInstance(Class<?> clz);

  @Alias("ensureClassInitialized")
  public static boolean ensureClassInitialized(Class<?> clazz) {
    try {
      Class.forName(clazz.getName(), true, clazz.getClassLoader());
      return true;
    } catch (Throwable ignored) {
    }
    return false;
  }

  @Alias("ensureClassInitializedForVisibly")
  private static boolean ensureClassInitializedForVisibly(Class<?> clazz) {
    try {
      Class.forName(clazz.getName(), true, clazz.getClassLoader());
      try {
        Constructor<?>[] declaredMethods = clazz.getDeclaredConstructors();
        if (declaredMethods.length > 0) {
          declaredMethods[0].newInstance();
        } else {
          clazz.getDeclaredMethods()[0].invoke(Albatross.class);
        }
      } catch (Throwable ignore) {
      }
      return ensureClassInitializedNative(clazz);
    } catch (Throwable ignored) {
    }
    return false;
  }

  public static boolean containsFlags(int flags) {
    return (albatross_flags & flags) != 0;
  }


  public static boolean isFieldEnable() {
    return !containsFlags(FLAG_FIELD_INVALID);
  }


  @TargetClass(targetExec = DO_NOTHING)
  private static class $Image {

    @ByName("ensureClassInitialized")
    static StaticMethodDef<Boolean> ensureClassInitialized;

    @ByName("ensureClassInitializedForVisibly")
    static StaticMethodDef<Boolean> ensureClassInitializedForVisibly;


    @ByName("appendLoader")
    static StaticMethodDef<Boolean> appendLoader;

    @ByName("checkMethodReturn")
    static StaticMethodDef<Set<?>> checkMethodReturn;

    @ByName("onClassInit")
    static StaticMethodDef<Boolean> onClassInit;
    @ByName("getCallerClass")
    static StaticMethodDef<Class<?>> getCallerClass;

    @ByName("currentApplication")
    static StaticMethodDef<Application> currentApplication;

    @ByName("currentPackageName")
    static StaticMethodDef<String> currentPackageName;

    @ByName("currentProcessName")
    static StaticMethodDef<String> currentProcessName;

    @ByName("backup")
    static StaticMethodDef<Boolean> backup;

    @ByName("backupAndHook")
    static StaticMethodDef<Boolean> backupAndHook;

    @ByName("hookClassInternal")
    static StaticMethodDef<Integer> hookClassInternal;

    @ByName("backupField")
    static StaticMethodDef<Boolean> backupField;
  }

  @TargetClass(targetExec = DO_NOTHING)
  private static class InstructionListenerH {
    @ByName("onEnter")
    static VoidMethodDef onEnter;
  }

  /**
   * .registers 4
   * .param p0, "a" # I
   * .param p1, "b" # I
   * .param p2, "c" # I
   * 00008cb4: 1300 0c00               0000: const/16            v0, 0xc
   * 00008cb8: 0f00                    0002: return              v0
   */


  private static int insLayoutMeasure(int a, int b, int c) {
    return 12;
  }

  private static int initResult;

  private static final Map<ClassLoader, List<Class<?>>> hookers = new HashMap<>();

  @SuppressLint({"BlockedPrivateApi", "SoonBlockedPrivateApi"})
  public static boolean init(int flags) {
    if ((initStatus & (STATUS_INIT_OK | STATUS_INIT_FAIL)) != 0) {
      return (initStatus & STATUS_INIT_OK) != 0;
    }
    albatross_flags = flags;
    try {
      if (containsFlags(FLAG_DEBUG)) {
        Debug.waitForDebugger();
      }
      Method initNativeMethod = Albatross.class.getDeclaredMethod("initMethodNative", Method.class, Method.class, int.class, Class.class);
      initResult = initMethodNative(initNativeMethod, Albatross.class.getDeclaredMethod("initFieldOffsetNative", Field.class, Field.class, int.class, Class.class),
          flags, Albatross.class);
      if (InitResultFlag.INIT_OK.isSet(initResult)) {
        initStatus |= STATUS_INIT_OK;
        initStatus &= ~(STATUS_INIT_FAIL | STATUS_NOT_INIT);
        if ((flags & FLAG_INIT_CLASS) != 0) {
          initClass = true;
        }
        if ((flags & FLAG_LOADER_FROM_CALLER) != 0) {
          loaderFromCaller = true;
        }
        toVisitedClass = new HashSet<>();
        initField();
        if (hookClassInternal($Image.class, Albatross.class.getClassLoader(), Albatross.class, null) == 0) {
          Albatross.log("should keep annotation info:" + $Image.class.getDeclaredField("ensureClassInitialized").getAnnotation(ByName.class)
              + ":" + Albatross.class.getDeclaredMethod("ensureClassInitialized", Class.class).getAnnotation(Alias.class));
          initStatus |= STATUS_INIT_FAIL;
          return false;
        } else {
//          Albatross.log("get hooker method success:" + $Image.ensureClassInitialized);
        }
        hookClassInternal(InstructionListenerH.class, Albatross.class.getClassLoader(), InstructionListener.class, null);
        transactionBegin(false);
        try {
          Method ensureClassInitialized = $Image.ensureClassInitialized.method;
          if (InitResultFlag.REPLACE_ENSURE_CLASS_INIT.isSet(initResult)) {
            Method ensureClassInitializedVisibly = $Image.ensureClassInitializedForVisibly.method;
            replace(ensureClassInitialized, ensureClassInitializedVisibly);
            ensureClassInitialized = ensureClassInitializedVisibly;
          }
          ensureClassInitialized(MethodStubs.class);
          registerMethodNative(ensureClassInitialized, $Image.onClassInit.method,
              $Image.appendLoader.method, $Image.checkMethodReturn.method, InstructionListenerH.onEnter.method, MethodStubs.class);
          int sdkInt = Build.VERSION.SDK_INT;
          if (sdkInt > 28 && sdkInt < 35) {
            Class<?> Reflection = Class.forName("sun.reflect.Reflection");
            addToVisit(Reflection);
            Albatross.backup(Reflection.getDeclaredMethod("getCallerClass"), $Image.getCallerClass.method, false, false, null, AOT | DISABLE_JIT, CURRENT);
          } else {
            Class<?> VMStack = Class.forName("dalvik.system.VMStack");
            addToVisit(VMStack);
            Albatross.backup(VMStack.getDeclaredMethod("getStackClass1"), $Image.getCallerClass.method, false, false, null, AOT | DISABLE_JIT, CURRENT);
          }
          Class<?> ActivityThread = Class.forName("android.app.ActivityThread");
          addToVisit(ActivityThread);
          Albatross.backup(ActivityThread.getDeclaredMethod("currentApplication"), $Image.currentApplication.method, false, false, null, AOT | DISABLE_JIT, CURRENT);
          Albatross.backup(ActivityThread.getDeclaredMethod("currentPackageName"), $Image.currentPackageName.method, false, false, null, AOT | DISABLE_JIT, CURRENT);
          Albatross.backup(ActivityThread.getDeclaredMethod("currentProcessName"), $Image.currentProcessName.method, false, false, null, AOT | DISABLE_JIT, CURRENT);
          defaultHookerBackupExecMode = INTERPRETER;
          if (Debug.isDebuggerConnected() || containsFlags(FLAG_NO_COMPILE)) {
            albatross_flags |= FLAG_NO_COMPILE;
            if (sdkInt <= 25) {
              disableFieldBackup();
            }
            defaultHookerExecMode = DO_NOTHING;
            defaultTargetExecMode = DO_NOTHING;
          } else {
            if (InitResultFlag.JIT_SUPPORTED.isSet(initResult)) {
              defaultHookerExecMode = JIT_OPTIMIZED;
              defaultTargetExecMode = JIT_OPTIMIZED;
            } else {
              albatross_flags |= FLAG_NO_COMPILE;
            }
            if (InitResultFlag.AOT_SUPPORTED.isSet(initResult)) {
              defaultHookerExecMode |= AOT;
              defaultTargetExecMode |= AOT;
            }
            if (sdkInt <= 25) {
              disableFieldBackup();
            } else {
              if (InitResultFlag.BACKUP_CALL_JIT.isSet(initResult))
                defaultHookerBackupExecMode = RECOMPILE_OPTIMIZED;
            }
          }
          if (!containsFlags(FLAG_NO_COMPILE)) {
            if (compileMethod($Image.backup.method)) {
              compileMethod($Image.backupAndHook.method);
              compileMethod($Image.hookClassInternal.method);
              compileMethod($Image.backupField.method);
            }
          }
          if (containsFlags(FLAG_DISABLE_LOG)) {
            albatross_flags &= ~FLAG_DISABLE_LOG;
            disableLog();
          }
          pendingMap = new HashMap<>();
          initClassLoader();
          Albatross.hookClassInternal(MethodCallHook.Image.class, MethodCallHook.class.getClassLoader(), MethodCallHook.class, null);
          Albatross.hookClassInternal(ActivityThreadH.class, ActivityThread.getClassLoader(), ActivityThread, null);
        } finally {
          transactionEnd(true);
        }
        registerHookCallback(new Method[]{MethodCallHook.Image.callVoid.method, MethodCallHook.Image.callBool.method, MethodCallHook.Image.callChar.method, MethodCallHook.Image.callByte.method,
            MethodCallHook.Image.callShort.method, MethodCallHook.Image.callInt.method, MethodCallHook.Image.callFloat.method, MethodCallHook.Image.callLong.method,
            MethodCallHook.Image.callDouble.method, MethodCallHook.Image.callObject.method});
        measureLayoutNative(Albatross.class.getDeclaredMethod("insLayoutMeasure", int.class, int.class, int.class));
        return true;
      } else {
        initStatus |= STATUS_INIT_FAIL | FLAG_FIELD_BACKUP_BAN;
      }
    } catch (Exception e) {
      Albatross.log("Albatross init:" + e, e);
    }
    return false;
  }

  public static String supportFeatures() {
    StringBuilder builder = new StringBuilder(64);
    if (InitResultFlag.JIT_SUPPORTED.isSet(initResult)) {
      builder.append("jit,");
    }
    if (InitResultFlag.AOT_SUPPORTED.isSet(initResult)) {
      builder.append("aot,");
    }
    if (InitResultFlag.BACKUP_CALL_JIT.isSet(initResult)) {
      builder.append("jit_backupCall,");
    }
    if (InitResultFlag.INS_ENABLE.isSet(initResult)) {
      builder.append("instruction,");
    }
    if (builder.length() > 0) {
      return builder.substring(0, builder.length() - 1);
    }
    return "";
  }

  private static void initClassLoader() throws AlbatrossException {
    syncClassLoader();
    hookClassInternal(ClassLoaderHook.class, ClassLoader.class.getClassLoader(), ClassLoader.class, null);
  }

  public static void log(String msg) {
    Log.i(TAG, msg);
  }

  public static void log(String msg, Throwable tr) {
    Log.e(TAG, msg, tr);
  }

  public static HookRecord putHook(HashMap<Object, HookRecord> hookRecord, Member target, Method hook, boolean isBackup, int targetExecMode, int hookerExecMode) throws AlbatrossException {
    HookRecord hookMethod;
    if ((hookMethod = hookRecord.get(target)) == null) {
      hookMethod = new HookRecord();
      hookMethod.target = target;
      hookRecord.put(target, hookMethod);
      if (isBackup)
        hookMethod.backup = hook;
      if (targetExecMode != CLASS_ALREADY_HOOK)
        hookMethod.targetExec = targetExecMode;
      else
        hookMethod.targetExec = DEFAULT_OPTION;
    } else {
      if (isBackup) {
        if (hookMethod.backup != null)
          throw new RepetitiveBackupErr(hook);
        hookMethod.backup = hook;
      }
      if (targetExecMode != CLASS_ALREADY_HOOK)
        hookMethod.targetExec = targetExecMode;
    }
    hookMethod.hook = hook;
    hookMethod.hookerExec = hookerExecMode;
    return hookMethod;
  }

  private static HookRecord putBackup(HashMap<Object, HookRecord> hookRecord, Member target, Method backup, int targetExecMode) throws AlbatrossException {
    HookRecord hookMethod;
    if (hookRecord.containsKey(target)) {
      hookMethod = hookRecord.get(target);
    } else {
      hookMethod = new HookRecord();
      hookMethod.target = target;
      hookRecord.put(target, hookMethod);
    }
    if (hookMethod.backup != null)
      throw new RepetitiveBackupErr(hookMethod.backup);
    hookMethod.backup = backup;
    hookMethod.targetExec = targetExecMode;
    return hookMethod;
  }

  private static BackupRecord putMirrorBackup(List<BackupRecord> records, Member target, Method backup, int targetExecMode, byte hookWay) {
    BackupRecord backupRecord = new BackupRecord();
    backupRecord.target = target;
    backupRecord.backup = backup;
    backupRecord.targetExec = targetExecMode;
    backupRecord.backupWay = hookWay;
    records.add(backupRecord);
    return backupRecord;
  }


  private static String getSplitValue(String split, String name) {
    int index = name.indexOf(split);
    return index > 0 ? name.substring(0, index) : name;
  }

  private static Class<?> getTargetClass(Class<?> targetClass, String[] className, Class<?> defaultClass, ClassLoader loader) throws ClassNotFoundException {
    if (targetClass != null && targetClass != Albatross.class && targetClass != TargetClass.class)
      return targetClass;
    if (className.length > 0) {
      if (loader != null)
        targetClass = findClass(className, loader);
      else
        targetClass = Albatross.findClass(className);
      if (targetClass != null) {
        if (initClass)
          return Class.forName(targetClass.getName(), initClass, targetClass.getClassLoader());
        addToVisit(targetClass);
        return targetClass;
      }
      throw new ClassNotFoundException(className[0]);
    }
    if (defaultClass == TargetClass.class)
      return TargetClass.class;
    if (defaultClass == null || defaultClass == Albatross.class)
      return null;
    if (initClass)
      Class.forName(defaultClass.getName(), true, loader);
    return defaultClass;
  }

  @Alias("getCallerClass")
  public native static Class<?> getCallerClass();


  public synchronized static void disableLog() {
    if (!containsFlags(FLAG_DISABLE_LOG)) {
      albatross_flags |= FLAG_DISABLE_LOG;
      try {
        disableMethod(Albatross.class.getDeclaredMethod("log", String.class), false);
        disableMethod(Albatross.class.getDeclaredMethod("log", String.class, Throwable.class), false);
      } catch (NoSuchMethodException e) {
        Albatross.log("disableLog", e);
      }
    }
  }

  public synchronized static void resetLogger(Method infoLogger, Method errLogger) {
    transactionBegin();
    try {
      backupAndHook(Albatross.class.getDeclaredMethod("log", String.class), infoLogger, null);
      backupAndHook(Albatross.class.getDeclaredMethod("log", String.class, Throwable.class), errLogger, null);
      transactionEnd(true);
    } catch (Throwable e) {
      transactionEnd(false);
      Albatross.log("resetLogger", e);
    }
  }


  public static boolean disableMethod(Method method) {
    return disableMethod(method, false);
  }

  public static native boolean disableMethod(Method method, boolean throwException);




  public static int hookClass() {
    Class<?> caller = getCallerClass();
    try {
      return hookClass(caller);
    } catch (AlbatrossErr e) {
      return REDUNDANT_ELEMENT;
    }
  }

  public static int hookClass(Class<?> hooker) throws AlbatrossErr {
    if (loaderFromCaller) {
      Class<?> caller = getCallerClass();
      ClassLoader loader = caller.getClassLoader();
      return hookClass(hooker, loader, null, null);
    } else {
      return hookClass(hooker, null, null, null);
    }
  }

  public static int hookClass(Class<?> hooker, Class<?> defaultClass) throws AlbatrossErr {
    return hookClass(hooker, defaultClass.getClassLoader(), defaultClass, null);
  }

  public static int hookObject(Class<?> hooker, Object instance) throws AlbatrossErr {
    Class<?> defaultClass = instance.getClass();
    return hookClass(hooker, defaultClass.getClassLoader(), defaultClass, instance);
  }

  public static int hookClass(Class<?> hooker, ClassLoader loader, Class<?> defaultClass, Object instance) throws AlbatrossErr {
    transactionBegin();
    boolean doTask = false;
    int res;
    try {
      res = hookClassInternal(hooker, loader, defaultClass, instance);
      doTask = true;
    } finally {
      transactionEnd(doTask);
    }
    return res;
  }

  public static int unhookClass(Class<?> hooker, ClassLoader loader, Class<?> defaultClass) throws AlbatrossErr {
    transactionBegin();
    boolean doTask = false;
    int res;
    try {
      res = unhookClassInternal(hooker, loader, defaultClass);
      if (res >= 0) {
        if (markUnhooked(hooker))
          unregisterHooker(hooker);
      }
      doTask = true;
    } finally {
      transactionEnd(doTask);
    }
    return res;
  }

  public static int unhookClass(Class<?> hooker) throws AlbatrossErr {
    return unhookClass(hooker, null, null);
  }

  public static int unhookClass(Class<?> hooker, Class<?> defaultClass) throws AlbatrossErr {
    return unhookClass(hooker, defaultClass.getClassLoader(), defaultClass);
  }

  public static void unloadDex(ClassLoader dexClassLoader) {
    List<Class<?>> dexHookers = null;
    synchronized (hookers) {
      dexHookers = hookers.remove(dexClassLoader);
    }
    if (dexHookers != null && !dexHookers.isEmpty()) {
      Albatross.transactionBegin();
      for (Class<?> c : dexHookers) {
        try {
          unhookClassInternal(c, null, null);
        } catch (AlbatrossErr ignore) {
        } catch (Throwable e) {
          Albatross.log("unhook class " + c + " err", e);
        }
      }
      Albatross.transactionEnd(true);
    }
  }


  private static Class<?>[] getArgumentTypes(Set<Class<?>> dependencies, Annotation[][] paramAnnotations, Class<?> targetClass, Class<?>[] mParameterTypes, ClassLoader loader, boolean targetStatic, boolean isHookStatic) throws ClassNotFoundException, FindMethodException, AlbatrossErr {
    Class<?>[] argTypes;
    CheckParameterTypesResult result;
    {
      result = checkParameterTypes(dependencies, mParameterTypes, paramAnnotations, loader);
      if (targetStatic == isHookStatic) {
        argTypes = mParameterTypes;
      } else if (targetStatic) {
        argTypes = new Class[mParameterTypes.length + 1];
        argTypes[0] = targetClass;
        System.arraycopy(mParameterTypes, 0, argTypes, 1, mParameterTypes.length);
        if (result != null) {
          result.offset = -1;
        }
      } else { //hook method is static Todo:check first argument
        Class<?> thisClass = mParameterTypes[0];
        if (!thisClass.isAssignableFrom(targetClass))
          checkTargetClass(dependencies, thisClass, targetClass);
        argTypes = Arrays.copyOfRange(mParameterTypes, 1, mParameterTypes.length);
        if (result != null) {
          result.offset = 1;
        }
      }
    }
    if (result != null) {
      throw new FindMethodException(argTypes, result);
    }
    return argTypes;
  }

  public static CheckParameterTypesResult checkParameterTypes(Set<Class<?>> dependencies, Class<?>[] mParameterTypes, Annotation[][] paramAnnotations, ClassLoader loader) throws ClassNotFoundException, AlbatrossErr {
    Class<?>[] mParameterSubTypes = null;
    Class<?>[] hookerClasses = null;
    byte[] primMatch = null;
    for (int i = 0; i < mParameterTypes.length; i++) {
      Class<?> clz = mParameterTypes[i];
      TargetClass parameterTarget = clz.getAnnotation(TargetClass.class);
      if (parameterTarget != null) {
        Class<?> realClz = getTargetClassFromAnnotation(parameterTarget, loader);
        if (realClz == null)
          throw new ClassNotFoundException(parameterTarget.className()[0]);
        else if (realClz == TargetClass.class) {
          if (mParameterSubTypes == null)
            mParameterSubTypes = new Class<?>[mParameterTypes.length];
          mParameterSubTypes[i] = Object.class;
          if (hookerClasses == null)
            hookerClasses = new Class<?>[mParameterTypes.length];
          hookerClasses[i] = clz;
        } else {
          mParameterTypes[i] = realClz;
          if (dependencies != null) {
            if (addAssignableHooker(clz, realClz))
              dependencies.add(clz);
          }
        }
      } else if (paramAnnotations != null) {
        Annotation[] annotations = paramAnnotations[i];
        for (Annotation annotation : annotations) {
          if (annotation instanceof ParamInfo) {
            String[] className = ((ParamInfo) annotation).value();
            Class<?> paramClass = Albatross.findClass(className);
            if (paramClass == null) {
              throw new ClassNotFoundException(className[0]);
            }
            if (!clz.isAssignableFrom(paramClass)) {
              throw new RuntimeException(String.format("params type %s is not assignable from %s", clz.getName(), paramClass.getName()));
            }
            mParameterTypes[i] = paramClass;
            break;
          } else if (annotation instanceof FuzzyMatch) {
            if (mParameterSubTypes == null)
              mParameterSubTypes = new Class<?>[mParameterTypes.length];
            mParameterSubTypes[i] = clz;
            byte size = ReflectUtils.getPrimSize(clz);
            if (size != 0) {
              if (primMatch == null)
                primMatch = new byte[mParameterTypes.length];
              primMatch[i] = size;
            }
          }
        }
      }
    }
    if (mParameterSubTypes == null)
      return null;
    CheckParameterTypesResult result = new CheckParameterTypesResult();
    result.mParameterSubTypes = mParameterSubTypes;
    result.hookerClasses = hookerClasses;
    result.primMatch = primMatch;
    return result;
  }

  private static Class<?> getTargetClassFromAnnotation(TargetClass targetClass, ClassLoader loader) {
    if (loader == null) {
      String[] className = targetClass.className();
      if (className.length > 0) {
        Class<?> clz = findClass(className);
        if (clz != null) {
          if (!initClass)
            return clz;
          try {
            return Class.forName(clz.getName(), initClass, clz.getClassLoader());
          } catch (Exception e) {
            return clz;
          }
        }
        return null;
      }
      return targetClass.value();
    } else {
      try {
        return getTargetClass(null, targetClass.className(), targetClass.value(), loader);
      } catch (ClassNotFoundException e) {
        return null;
      }
    }
  }

  public static void disableAlbatross() {
    initStatus |= STATUS_DISABLED;
  }

  public static void disableFieldBackup() {
    albatross_flags |= FLAG_FIELD_DISABLED;
  }

  public static void enableAlbatross() {
    initStatus &= ~STATUS_DISABLED;
  }

  private static int defaultHookerExecMode = DO_NOTHING;
  private static int defaultHookerBackupExecMode = DO_NOTHING;
  private static int defaultTargetExecMode = DO_NOTHING;

  public static void setExecConfiguration(int targetExecMode, int hookerExecMode) {
    if (containsFlags(FLAG_NO_COMPILE))
      defaultHookerBackupExecMode = INTERPRETER;
    else
      defaultHookerBackupExecMode = RECOMPILE_OPTIMIZED;
    defaultTargetExecMode = targetExecMode;
    defaultHookerExecMode = hookerExecMode;
  }

  public static void disableCompileBackupCall() {
    defaultHookerBackupExecMode = INTERPRETER;
  }

  public static void setExecConfiguration(int targetExecMode, int hookerExecMode, int hookerBackupExec) {
    defaultTargetExecMode = targetExecMode;
    defaultHookerExecMode = hookerExecMode;
    defaultHookerBackupExecMode = hookerBackupExec | DECOMPILE;
  }

  enum HookAction {
    HOOK_METHOD(0), BACKUP_METHOD(1), HOOK_CONSTRUCTOR(2), BACKUP_CONSTRUCTOR(3);
    final int v;

    HookAction(int v) {
      this.v = v;
    }
  }

  private static void registerHooker(Class<?> hooker) {
    ClassLoader loader = hooker.getClassLoader();
    synchronized (hookers) {
      List<Class<?>> classLoaderHookers = hookers.computeIfAbsent(loader, k -> new ArrayList<>());
      classLoaderHookers.add(hooker);
    }
  }

  private static void unregisterHooker(Class<?> hooker) {
    ClassLoader loader = hooker.getClassLoader();
    synchronized (hookers) {
      List<Class<?>> classLoaderHookers = hookers.get(loader);
      if (classLoaderHookers != null) {
        classLoaderHookers.remove(hooker);
      }
    }
  }

  @Alias("hookClassInternal")
  private static int hookClassInternal(Class<?> hooker, ClassLoader loader, Class<?> defaultClass, Object instance) throws AlbatrossErr {
    if (initStatus > STATUS_INIT_OK) {
      return 0;
    }
    if (!markHooked(hooker)) {
      if (defaultClass == null || (!pendingMap.containsKey(defaultClass.getName())))
        return CLASS_ALREADY_HOOK;
    }
    registerHooker(hooker);
    HashMap<Object, HookRecord> hookRecord = new HashMap<>();
    List<BackupRecord> backupRecords = new ArrayList<>();
    try {
      if (!isHookerAssignableNative(hooker, null))
        ensureClassInitialized(hooker);
    } catch (Exception e) {
      throw new RuntimeException("init hook class fail", e);
    }
    int hookerDefaultExecOption = defaultHookerExecMode;
    int hookerBackupDefaultExecOption = defaultHookerBackupExecMode;
    int targetDefaultExecOption = defaultTargetExecMode;
    boolean isDebug = Debug.isDebuggerConnected();
    TargetClass targetClassAnno = hooker.getAnnotation(TargetClass.class);
    boolean isMirror = true;
    if (targetClassAnno != null) {
      isMirror = targetClassAnno.image();
      if (!isDebug) {
        int hookerExecOption = targetClassAnno.hookerExec();
        int targetExecOption = targetClassAnno.targetExec();
        int hookerBackupExecOption = targetClassAnno.hookerBackupExec();
        if (hookerExecOption != DEFAULT_OPTION) {
          hookerDefaultExecOption = hookerExecOption;
        }
        if (targetExecOption != DEFAULT_OPTION) {
          targetDefaultExecOption = targetExecOption;
        }
        if (hookerBackupExecOption != DEFAULT_OPTION) {
          hookerBackupDefaultExecOption = hookerBackupExecOption;
        }
      }
    }
    if (defaultClass == null) {
      if (targetClassAnno != null) {
        defaultClass = getTargetClassFromAnnotation(targetClassAnno, loader);
        if (defaultClass == TargetClass.class)
          defaultClass = null;
        if (defaultClass != null) {
          if (loader == null)
            loader = defaultClass.getClassLoader();
        } else if (targetClassAnno.pendingHook()) {
          String[] classNames = targetClassAnno.className();
          for (String className : classNames) {
            addPendingHook(className, hooker);
          }
          return 0;
        } else if (targetClassAnno.required()) {
          throw new RequiredClassErr(targetClassAnno);
        }
      }
    } else {
      if (loader == null) {
        loader = defaultClass.getClassLoader();
      }
    }
    if (defaultClass != null) {
      if (defaultClass.isInterface())
        throw new HookInterfaceErr(defaultClass);
      if (!(defaultClass.getClassLoader() instanceof BaseDexClassLoader)) {
        addToVisit(defaultClass);
      } else if (targetClassAnno == null) {
        isMirror = hooker != defaultClass;
        if (isMirror) {
          Class<?> superclass = hooker.getSuperclass();
          if (superclass != null && superclass != Object.class) {
            if (defaultClass != null)
              if (ReflectUtils.isInstanceOf(hooker, defaultClass))
                isMirror = false;
            if (isMirror) {
              throw new MirrorExtendErr(hooker);
            }
          }
        }
      }
    }
    if (isMirror) {
      banInstance(hooker);
    }
    int runModeAnnotationCount = 0;
    int successCount = 0;
    Field[] fields = hooker.getDeclaredFields();
    Set<Class<?>> dependencies = new HashSet<>();
    Map<String, Object> slotMap = new HashMap<>();
    boolean fieldEnable = !containsFlags(FLAG_FIELD_INVALID);
    boolean staticEnable = containsFlags(FLAG_FIELD_BACKUP_STATIC);
    for (Field field : fields) {
      boolean isStatic = Modifier.isStatic(field.getModifiers());
      if (fieldEnable) {
        FieldRef fieldRef = field.getAnnotation(FieldRef.class);
        if (fieldRef != null /*|| !isStatic*/) {
          if (isStatic && !staticEnable)
            continue;
          try {
            Class<?> targetClass;
            targetClass = getTargetClass(fieldRef.targetClass(), fieldRef.className(), defaultClass, loader);
            Field targetField;
            int option = fieldRef.option();
            if ((option & DefOption.VIRTUAL) == 0) {
              try {
                targetField = targetClass.getDeclaredField(field.getName());
              } catch (NoSuchFieldException e) {
                targetField = ReflectUtils.findDeclaredField(targetClass, fieldRef.value());
              }
            } else {
              try {
                targetField = ReflectUtils.findField(targetClass, field.getName());
              } catch (NoSuchFieldException e) {
                targetField = ReflectUtils.findField(targetClass, fieldRef.value());
              }
            }
            Class<?> targetType;
            if ((option & DefOption.INSTANCE) == 0) {
              targetType = null;
            } else {
              Object fieldValue;
              targetField.setAccessible(true);
              try {
                if (isStatic) {
                  fieldValue = targetField.get(null);
                } else {
                  if (instance == null) {
                    throw new RequiredInstanceErr(targetField);
                  }
                  fieldValue = targetField.get(instance);
                }
              } catch (IllegalAccessException e) {
                fieldValue = null;
              }
              if (fieldValue != null)
                targetType = fieldValue.getClass();
              else
                targetType = null;
            }
            if (backupField(dependencies, targetField, field, targetType)) {
              successCount += 1;
              hookerDefaultExecOption &= ~AOT;
              hookerDefaultExecOption |= INTERPRETER;
              log("Field " + targetField);
            }
          } catch (NoSuchFieldException | FieldException e) {
            if (fieldRef.required())
              throw new RequiredFieldErr(fieldRef);
            log("fail get field", e);
          } catch (ClassNotFoundException e) {
            log("fail get field target class", e);
          }
          continue;
        }
      }
      if (defaultClass == null)
        continue;
      Class<?> type = field.getType();
      if (!isStatic) {
        if (field.getAnnotation(FieldRef.class) == null) {
          if (isMirror)
            throw new RedundantFieldErr(field);
          continue;
        }
      } else if (type == Class.class) {
        if (field.getName().equals("Class")) {
          field.setAccessible(true);
          try {
            field.set(null, defaultClass);
          } catch (IllegalAccessException e) {
            log("fail set class value:" + field, e);
          }
        }
        continue;
      }
      FieldConfig fieldConfig = fieldClsMap.get(type);
      if (fieldConfig != null) {
        try {
          field.setAccessible(true);
          Constructor<? extends ReflectionBase> constructor = fieldConfig.constructor;
          if (!fieldConfig.needClz) {
            Field targetField = ReflectUtils.findField(defaultClass, field.getName());
            Class<?> expectClass = fieldConfig.clz;
            Class<?> targetFieldType = targetField.getType();
            if (expectClass != null) {
              if (expectClass != targetFieldType) {
                log(String.format("wrong filed %s type,expect %s get %s", field.getName(), expectClass.getName(), targetFieldType.getName()));
                continue;
              }
            } else {
              expectClass = ReflectionBase.getFieldGenericType(field);
              if (expectClass != null) {
                if (!expectClass.isAssignableFrom(targetFieldType)) {
                  if (!checkTargetClass(dependencies, expectClass, targetFieldType)) {
                    log(String.format("wrong filed %s type,expect %s get %s", field.getName(), expectClass.getName(), targetFieldType.getName()));
                    continue;
                  }
                }
              }
            }
            targetField.setAccessible(true);
            ReflectionBase newFieldDef = constructor.newInstance(targetField);
            field.set(null, newFieldDef);
          } else {
            ArgumentTypeSlot argumentTypeSlot = field.getAnnotation(ArgumentTypeSlot.class);
            if (argumentTypeSlot != null) {
              String slotName = argumentTypeSlot.value();
              if (slotName.isEmpty())
                slotName = field.getName();
              slotMap.put(slotName, new Object[]{field, fieldConfig});
              continue;
            }
            ReflectionBase newFieldDef = constructor.newInstance(dependencies, defaultClass, field);
            Class<?> expectClass = ReflectionBase.getFieldGenericType(field);
            if (expectClass == null)
              expectClass = newFieldDef.getExpectType();
            if (expectClass != null && expectClass != Object.class) {
              Class<?> realType = newFieldDef.getRealType();
              if (realType.isPrimitive() && !expectClass.isPrimitive()) {
                realType = primBoxMap.get(realType);
              }
              if ((!expectClass.isAssignableFrom(realType))) {
                if (!checkTargetClass(dependencies, expectClass, realType)) {
                  log(String.format("wrong filed %s type,expect %s get %s", field.getName(), expectClass.getName(), realType.getName()));
                  continue;
                }
              }
            }
            field.set(null, newFieldDef);
          }
          successCount += 1;
        } catch (Exception e) {
          Albatross.log("Reflection", e);
        }
      }
    }
    boolean checkArgument = false;
    String methodName;
    MethodHookBackup hookBackup;
    StaticMethodHook staticMethodHook;
    MethodHook methodHook;
    StaticMethodHookBackup staticMethodHookBackup;
    ConstructorHook constructorHook;
    ConstructorHookBackup constructorHookBackup;
    MethodBackup methodBackup;
    StaticMethodBackup staticMethodBackup;
    ConstructorBackup constructorBackup;
    boolean needBackup = false;
    String[] aliases = null;
    int sdk = Build.VERSION.SDK_INT;
    int hookerExec = hookerDefaultExecOption;
    boolean methodRequired;
    byte methodOption;
    byte callWay = CURRENT;
    for (Method m : hooker.getDeclaredMethods()) {
      Annotation[] annotations = m.getAnnotations();
      if (annotations.length == 0) {
        if (!Modifier.isStatic(m.getModifiers()))
          if (isMirror)
            throw new RedundantMethodErr(m);
        continue;
      }
      byte minSdk;
      byte maxSdk;
      boolean targetStatic;
      Class<?> targetClass;
      String[] className;
      String[] classNameArgs;
      HookAction hookWay;
      ByName methodMarkAlias;
      int targetExec = CLASS_ALREADY_HOOK;
      if ((hookBackup = m.getAnnotation(MethodHookBackup.class)) != null) {
        targetStatic = hookBackup.isStatic();
        className = hookBackup.className();
        classNameArgs = hookBackup.value();
        needBackup = true;
        targetClass = hookBackup.targetClass();
        aliases = hookBackup.name();
        hookWay = HookAction.HOOK_METHOD;
        hookerExec = hookBackup.hookerExec();
        targetExec = hookBackup.targetExec();
        methodRequired = hookBackup.required();
        methodOption = hookBackup.option();
        minSdk = hookBackup.minSdk();
        maxSdk = hookBackup.maxSdk();
        callWay = hookBackup.callWay();
      } else if ((methodHook = m.getAnnotation(MethodHook.class)) != null) {
        targetStatic = methodHook.isStatic();
        targetClass = methodHook.targetClass();
        classNameArgs = methodHook.value();
        hookerExec = methodHook.hookerExec();
        if ((methodBackup = m.getAnnotation(MethodBackup.class)) != null) {
          needBackup = true;
          targetExec = methodBackup.targetExec();
          callWay = methodBackup.callWay();
        } else
          needBackup = false;
        aliases = methodHook.name();
        className = methodHook.className();
        hookWay = HookAction.HOOK_METHOD;
        methodRequired = methodHook.required();
        methodOption = methodHook.option();
        minSdk = methodHook.minSdk();
        maxSdk = methodHook.maxSdk();
      } else if ((staticMethodHook = m.getAnnotation(StaticMethodHook.class)) != null) {
        targetStatic = true;
        targetClass = staticMethodHook.targetClass();
        classNameArgs = staticMethodHook.value();
        hookerExec = staticMethodHook.hookerExec();
        if ((staticMethodBackup = m.getAnnotation(StaticMethodBackup.class)) != null) {
          targetExec = staticMethodBackup.targetExec();
          needBackup = true;
          callWay = staticMethodBackup.callWay();
        } else
          needBackup = false;
        className = staticMethodHook.className();
        aliases = staticMethodHook.name();
        hookWay = HookAction.HOOK_METHOD;
        methodRequired = staticMethodHook.required();
        methodOption = staticMethodHook.option();
        minSdk = staticMethodHook.minSdk();
        maxSdk = staticMethodHook.maxSdk();
      } else if ((staticMethodHookBackup = m.getAnnotation(StaticMethodHookBackup.class)) != null) {
        targetStatic = true;
        targetClass = staticMethodHookBackup.targetClass();
        classNameArgs = staticMethodHookBackup.value();
        needBackup = true;
        aliases = staticMethodHookBackup.name();
        hookWay = HookAction.HOOK_METHOD;
        className = staticMethodHookBackup.className();
        hookerExec = staticMethodHookBackup.hookerExec();
        targetExec = staticMethodHookBackup.targetExec();
        methodRequired = staticMethodHookBackup.required();
        methodOption = staticMethodHookBackup.option();
        minSdk = staticMethodHookBackup.minSdk();
        maxSdk = staticMethodHookBackup.maxSdk();
        callWay = staticMethodHookBackup.callWay();
      } else if ((constructorHook = m.getAnnotation(ConstructorHook.class)) != null) {
        hookWay = HookAction.HOOK_CONSTRUCTOR;
        targetStatic = false;
        targetClass = constructorHook.targetClass();
        classNameArgs = constructorHook.value();
        hookerExec = constructorHook.hookerExec();
        if ((constructorBackup = m.getAnnotation(ConstructorBackup.class)) != null) {
          targetExec = constructorBackup.targetExec();
          needBackup = true;
          callWay = constructorBackup.callWay();
        } else
          needBackup = false;
        className = constructorHook.className();
        methodRequired = constructorHook.required();
        methodOption = constructorHook.option();
        minSdk = constructorHook.minSdk();
        maxSdk = constructorHook.maxSdk();
      } else if ((constructorHookBackup = m.getAnnotation(ConstructorHookBackup.class)) != null) {
        hookWay = HookAction.HOOK_CONSTRUCTOR;
        targetClass = constructorHookBackup.targetClass();
        classNameArgs = constructorHookBackup.value();
        className = constructorHookBackup.className();
        needBackup = true;
        targetStatic = false;
        hookerExec = constructorHookBackup.hookerExec();
        targetExec = constructorHookBackup.targetExec();
        methodRequired = constructorHookBackup.required();
        methodOption = constructorHookBackup.option();
        minSdk = constructorHookBackup.minSdk();
        maxSdk = constructorHookBackup.maxSdk();
        callWay = constructorHookBackup.callWay();
      } else if ((methodBackup = m.getAnnotation(MethodBackup.class)) != null) {
        targetStatic = methodBackup.isStatic();
        targetClass = methodBackup.targetClass();
        className = methodBackup.className();
        classNameArgs = methodBackup.value();
        hookWay = HookAction.BACKUP_METHOD;
        aliases = methodBackup.name();
        targetExec = methodBackup.targetExec();
        methodRequired = methodBackup.required();
        methodOption = methodBackup.option();
        minSdk = methodBackup.minSdk();
        maxSdk = methodBackup.maxSdk();
        callWay = methodBackup.callWay();
      } else if ((staticMethodBackup = m.getAnnotation(StaticMethodBackup.class)) != null) {
        targetStatic = true;
        targetClass = staticMethodBackup.targetClass();
        className = staticMethodBackup.className();
        classNameArgs = staticMethodBackup.value();
        aliases = staticMethodBackup.name();
        hookWay = HookAction.BACKUP_METHOD;
        targetExec = staticMethodBackup.targetExec();
        methodRequired = staticMethodBackup.required();
        methodOption = staticMethodBackup.option();
        minSdk = staticMethodBackup.minSdk();
        maxSdk = staticMethodBackup.maxSdk();
        callWay = staticMethodBackup.callWay();
      } else if ((constructorBackup = m.getAnnotation(ConstructorBackup.class)) != null) {
        targetClass = constructorBackup.targetClass();
        classNameArgs = constructorBackup.value();
        className = constructorBackup.className();
        hookWay = HookAction.BACKUP_CONSTRUCTOR;
        targetStatic = false;
        targetExec = constructorBackup.targetExec();
        methodRequired = constructorBackup.required();
        methodOption = constructorBackup.option();
        minSdk = constructorBackup.minSdk();
        maxSdk = constructorBackup.maxSdk();
        callWay = constructorBackup.callWay();
      } else {
        if (!Modifier.isStatic(m.getModifiers()))
          if (isMirror)
            throw new RedundantMethodErr(m);
        RunMode runMode = m.getAnnotation(RunMode.class);
        if (runMode != null) {
          runModeAnnotationCount += 1;
        }
        continue;
      }
      if (minSdk != 0) {
        if (sdk < minSdk)
          continue;
      }
      if (maxSdk != 0) {
        if (sdk > maxSdk)
          continue;
      }
      Class<?>[] mParameterTypes = m.getParameterTypes();
      Annotation[][] parameterAnnotations = m.getParameterAnnotations();
      boolean isHookStatic = Modifier.isStatic(m.getModifiers());
      if (targetClass == TargetClass.class) {
        try {
          if (!targetStatic && isHookStatic && mParameterTypes.length == 0) {
            throw new HookerStructErr("An instance method must have at least one parameter to save this");
          }
          targetClass = getTargetClass(defaultClass, className, targetStatic ? null : (isHookStatic ? mParameterTypes[0] : null), loader);
        } catch (ClassNotFoundException e) {
          log("Cannot find target class for " + m + ":" + e);
          if (methodRequired) {
            throw new RequiredMethodErr("required method target class is not find", annotations[0]);
          }
          continue;
        }
      }

      Class<?>[] argTypes;
      Method targetMethod = null;
      Constructor<?> targetConstructor = null;
      if (hookWay.v <= HookAction.BACKUP_METHOD.v && (methodMarkAlias = m.getAnnotation(ByName.class)) != null) {
        argTypes = null;
        try {
          if ((methodOption & DefOption.VIRTUAL) == 0)
            targetMethod = ReflectUtils.findDeclaredMethodByName(targetClass, methodMarkAlias.value(), methodMarkAlias.onlyAnno());
          else
            targetMethod = ReflectUtils.findMethodByName(targetClass, methodMarkAlias.value(), methodMarkAlias.onlyAnno());
          checkArgument = true;
        } catch (NoSuchMethodException ignore) {
          log("Wrong target method for " + m);
          if (methodRequired) {
            throw new RequiredMethodErr("Wrong target method for " + m, annotations[0]);
          }
        }
      } else {
        try {
          if (classNameArgs.length > 0) {
            argTypes = getArgumentTypesFromString(classNameArgs, loader, false);
            checkArgument = true;
          } else {
            checkArgument = false;
            argTypes = getArgumentTypes(dependencies, parameterAnnotations, targetClass, mParameterTypes, loader, targetStatic, isHookStatic);
          }
        } catch (ClassNotFoundException e) {
          if (methodRequired) {
            throw new RequiredMethodErr("required method argument class is not find:" + e.getMessage(), annotations[0]);
          }
          log("Cannot find target method argument for " + m);
          continue;
        } catch (FindMethodException e) {
          CheckParameterTypesResult subArgTypes = e.subArgTypes;
          if (!isMirror)
            subArgTypes.excludeMethod = m;
          if (hookWay.v <= HookAction.BACKUP_METHOD.v) {
            String name;
            if (hookWay == HookAction.HOOK_METHOD)
              name = getSplitValue(HOOK_SUFFIX, m.getName());
            else
              name = getSplitValue(BACKUP_SUFFIX, m.getName());
            if ((methodOption & DefOption.VIRTUAL) == 0) {
              targetMethod = ReflectUtils.findDeclaredMethodWithType(targetClass, name, e.argTypes, subArgTypes);
              if (targetMethod == null) {
                for (String alias : aliases) {
                  targetMethod = ReflectUtils.findDeclaredMethodWithType(targetClass, alias, e.argTypes, subArgTypes);
                  if (targetMethod != null)
                    break;
                }
              }
            } else {
              targetMethod = ReflectUtils.findMethodWithType(targetClass, name, e.argTypes, subArgTypes);
              if (targetMethod == null) {
                for (String alias : aliases) {
                  targetMethod = ReflectUtils.findMethodWithType(targetClass, alias, e.argTypes, subArgTypes);
                  if (targetMethod != null)
                    break;
                }
              }
            }
            if (targetMethod == null) {
              log("Cannot find target method argument for " + m);
              if (methodRequired) {
                throw new RequiredMethodErr("required method argument class is not find:" + e.getMessage(), annotations[0]);
              }
              continue;
            }
          } else {
            targetConstructor = ReflectUtils.findDeclaredConstructorWithType(targetClass, e.argTypes, subArgTypes);
            if (targetConstructor == null) {
              log("Cannot find target constructor  for " + m);
              if (methodRequired) {
                throw new RequiredMethodErr("required target constructor is not find:" + e.getMessage(), annotations[0]);
              }
              continue;
            }
          }
          argTypes = e.argTypes;
        }
      }
      switch (hookWay) {
        case HOOK_METHOD:
        case BACKUP_METHOD: {
          if (targetMethod == null) {
            try {
              if (hookWay == HookAction.HOOK_METHOD)
                methodName = getSplitValue(HOOK_SUFFIX, m.getName());
              else
                methodName = getSplitValue(BACKUP_SUFFIX, m.getName());
              if ((methodOption & DefOption.VIRTUAL) == 0)
                targetMethod = targetClass.getDeclaredMethod(methodName, argTypes);
              else
                targetMethod = ReflectUtils.findMethod(targetClass, methodName, argTypes);
            } catch (Exception e) {
              for (String alias : aliases) {
                try {
                  if ((methodOption & DefOption.VIRTUAL) == 0)
                    targetMethod = targetClass.getDeclaredMethod(alias, argTypes);
                  else
                    targetMethod = ReflectUtils.findMethod(targetClass, alias, argTypes);
                  break;
                } catch (Exception ignore) {
                }
              }
            }
          }
          if (targetMethod != null) {
            try {
              checkMethodReturn(dependencies, targetMethod, m);
              if ((methodOption & DefOption.NOTHING) == 0) {
                if (hookWay == HookAction.HOOK_METHOD)
                  putHook(hookRecord, targetMethod, m, needBackup, targetExec, hookerExec).checkMethSign = checkArgument;
                else if (callWay == CURRENT)
                  putBackup(hookRecord, targetMethod, m, targetExec).checkMethSign = checkArgument;
                else {
                  putMirrorBackup(backupRecords, targetMethod, m, targetExec, callWay).checkMethSign = checkArgument;
                }
              }
            } catch (AlbatrossErr e) {
              throw e;
            } catch (Exception e) {
              log("Wrong target method for " + m, e);
              if (methodRequired) {
                throw new RequiredMethodErr("Wrong target method for " + m, annotations[0]);
              }
            }
          } else {
            log("Cannot find target method for " + m);
            if (methodRequired) {
              throw new RequiredMethodErr("Cannot find target method for " + m, annotations[0]);
            }
          }
          break;
        }
        case HOOK_CONSTRUCTOR:
        case BACKUP_CONSTRUCTOR: {
          if (targetConstructor == null) {
            try {
              targetConstructor = targetClass.getDeclaredConstructor(argTypes);
            } catch (NoSuchMethodException e) {
              log("Cannot find target constructor for " + m);
              if (methodRequired) {
                throw new RequiredMethodErr("Cannot find target constructor for " + m, annotations[0]);
              }
            }
          }
          if (targetConstructor != null) {
            try {
              checkMethodReturn(dependencies, targetConstructor, m);
              if ((methodOption & DefOption.NOTHING) == 0) {
                if (hookWay == HookAction.HOOK_CONSTRUCTOR)
                  putHook(hookRecord, targetConstructor, m, needBackup, targetExec, hookerExec).checkMethSign = checkArgument;
                else if (callWay == CURRENT)
                  putBackup(hookRecord, targetConstructor, m, targetExec).checkMethSign = checkArgument;
                else {
                  putMirrorBackup(backupRecords, targetMethod, m, targetExec, callWay).checkMethSign = checkArgument;
                }
              }
            } catch (AlbatrossErr e) {
              throw e;
            } catch (Exception e) {
              log("Wrong target constructor for " + m, e);
              if (methodRequired) {
                throw new RequiredMethodErr("Wrong target constructor for " + m, annotations[0]);
              }
            }
          }
          break;
        }
      }
    }
    String[] methodNames = new String[2];
    Collection<HookRecord> hookRecords = hookRecord.values();
    for (HookRecord hookMethod : hookRecords) {
      Method hook = hookMethod.hook;
      Method backup = hookMethod.backup;
      Member target = hookMethod.target;
      try {
        boolean result;
        if (hook != null) {
          if (hookMethod.targetExec == DEFAULT_OPTION)
            hookMethod.targetExec = targetDefaultExecOption;
          if (hookMethod.hookerExec == DEFAULT_OPTION) {
            if (hook == backup)
              hookMethod.hookerExec = hookerBackupDefaultExecOption;
            else
              hookMethod.hookerExec = hookerDefaultExecOption;
          }
          result = Albatross.backupAndHook(target, hook, backup, hookMethod.checkMethSign, false, dependencies, hookMethod.targetExec, hookMethod.hookerExec);
        } else {
          if (hookMethod.targetExec == DEFAULT_OPTION)
            hookMethod.targetExec = targetDefaultExecOption;
          result = Albatross.backup(target, backup, hookMethod.checkMethSign, false, dependencies, hookMethod.targetExec, CURRENT);
        }
        if (result) {
          if (!slotMap.isEmpty()) {
            if (hook != null)
              methodNames[0] = hook.getName();
            if (backup != null)
              methodNames[1] = backup.getName();
            for (String name : methodNames) {
              if (name == null)
                continue;
              if (slotMap.containsKey(name)) {
                Object[] v = (Object[]) slotMap.get(name);
                FieldConfig fieldConfig = (FieldConfig) v[1];
                Field field = (Field) v[0];
                ReflectionBase slot_field = fieldConfig.constructor_slot.newInstance(target);
                Class<?> expectClass = ReflectionBase.getFieldGenericType(field);
                boolean checkFail = false;
                if (expectClass != null && expectClass != Object.class) {
                  Class<?> realType = slot_field.getRealType();
                  if ((!expectClass.isAssignableFrom(realType)) && !(realType == void.class && expectClass == Void.class)) {
                    if (!checkTargetClass(dependencies, expectClass, realType)) {
                      checkFail = true;
                      log(String.format("wrong filed %s type,expect %s get %s", field.getName(), expectClass.getName(), realType.getName()));
                    }
                  }
                }
                if (!checkFail) {
                  field.set(null, slot_field);
                  successCount += 1;
                }
                slotMap.remove(name);
              }
            }
          }
          successCount += 1;
          if (hook != null) {
            if (hook == backup)
              log("Hooked " + target + ": hookBackup=" + hook);
            else
              log("Hooked " + target + ": hook=" + hook + (backup == null ? "" : ", backup=" + backup));
          } else
            log("Backup " + target + ": backup=" + backup);
        }
      } catch (AlbatrossErr e) {
        log("Failed to hook  " + target, e);
        throw e;
      } catch (Throwable t) {
        log("Failed to hook " + target + ": hook=" + hook + (backup == null ? "" : ", backup=" + backup), t);
      }
    }

    for (BackupRecord backupRecord : backupRecords) {
      Method backup = backupRecord.backup;
      Member target = backupRecord.target;
      try {
        boolean result;
        if (backupRecord.targetExec == DEFAULT_OPTION)
          backupRecord.targetExec = targetDefaultExecOption;
        result = Albatross.backup(target, backup, backupRecord.checkMethSign, false, dependencies, backupRecord.targetExec, backupRecord.backupWay);
        if (result) {
          if (!slotMap.isEmpty()) {
            String name = backup.getName();
            if (slotMap.containsKey(name)) {
              Object[] v = (Object[]) slotMap.get(name);
              FieldConfig fieldConfig = (FieldConfig) v[1];
              Field field = (Field) v[0];
              ReflectionBase slot_field = fieldConfig.constructor_slot.newInstance(target);
              Class<?> expectClass = ReflectionBase.getFieldGenericType(field);
              boolean checkFail = false;
              if (expectClass != null && expectClass != Object.class) {
                Class<?> realType = slot_field.getRealType();
                if ((!expectClass.isAssignableFrom(realType)) && !(realType == void.class && expectClass == Void.class)) {
                  if (!checkTargetClass(dependencies, expectClass, realType)) {
                    checkFail = true;
                    log(String.format("wrong filed %s type,expect %s get %s", field.getName(), expectClass.getName(), realType.getName()));
                  }
                }
              }
              if (!checkFail) {
                field.set(null, slot_field);
                successCount += 1;
              }
              slotMap.remove(name);
            }
          }
          successCount += 1;
          log("Mirror " + target + ": backup=" + backup);
        }
      } catch (AlbatrossErr e) {
        log("Failed to mirror  " + target, e);
        throw e;
      } catch (Throwable t) {
        log("Failed to mirror " + target + ": backup=" + backup, t);
      }
    }


    if (!isDebug) {
      if (hookerDefaultExecOption != DO_NOTHING) {
        compileClass(hooker, hookerDefaultExecOption);
      }
      if (defaultClass != null && targetDefaultExecOption != DO_NOTHING) {
        if (runModeAnnotationCount == 0)
          compileClass(defaultClass, hookerDefaultExecOption);
        else
          compileClassByAnnotation(defaultClass, hookerDefaultExecOption);
      }
    }
    if (!dependencies.isEmpty()) {
      successCount += hookDependency(dependencies);
    }
    return successCount;
  }


  private static int unhookClassInternal(Class<?> hooker, ClassLoader loader, Class<?> defaultClass) throws AlbatrossErr {
    if (initStatus > STATUS_INIT_OK) {
      return -2;
    }
    if (!isHooked(hooker)) {
      return -1;
    }
    HashMap<Object, HookRecord> hookRecord = new HashMap<>();
    List<BackupRecord> backupRecords = new ArrayList<>();
    TargetClass targetClassAnno = hooker.getAnnotation(TargetClass.class);
    boolean isMirror = true;
    if (targetClassAnno != null) {
      isMirror = targetClassAnno.image();
    }
    if (defaultClass == null) {
      if (targetClassAnno != null) {
        defaultClass = getTargetClassFromAnnotation(targetClassAnno, loader);
        if (defaultClass == TargetClass.class)
          defaultClass = null;
        if (defaultClass != null) {
          if (loader == null)
            loader = defaultClass.getClassLoader();
        } else if (targetClassAnno.required()) {
          return 0;
        }
      }
    } else {
      if (loader == null) {
        loader = defaultClass.getClassLoader();
      }
    }
    if (defaultClass != null) {
      if (defaultClass.isInterface())
        return 0;
      if (!(defaultClass.getClassLoader() instanceof BaseDexClassLoader)) {
        addToVisit(defaultClass);
      } else if (targetClassAnno == null) {
        isMirror = hooker != defaultClass;
        if (isMirror) {
          Class<?> superclass = hooker.getSuperclass();
          if (superclass != null && superclass != Object.class) {
            if (defaultClass != null)
              if (ReflectUtils.isInstanceOf(hooker, defaultClass))
                isMirror = false;
            if (isMirror) {
              throw new MirrorExtendErr(hooker);
            }
          }
        }
      }
    }
    int successCount;
    boolean fieldEnable = !containsFlags(FLAG_FIELD_INVALID);
    if (fieldEnable)
      successCount = unBackupHookerFieldNative(hooker);
    else
      successCount = 0;
    Field[] fields = hooker.getDeclaredFields();
    Set<Class<?>> dependencies = null;
    Map<String, Object> slotMap = new HashMap<>();

    if (defaultClass != null) {
      for (Field field : fields) {
        boolean isStatic = Modifier.isStatic(field.getModifiers());
        if (!isStatic)
          continue;
        Class<?> type = field.getType();
        if (type == Class.class) {
          if (field.getName().equals("Class")) {
            field.setAccessible(true);
            try {
              field.set(null, null);
            } catch (IllegalAccessException e) {
            }
          }
          continue;
        }
        FieldConfig fieldConfig = fieldClsMap.get(type);
        if (fieldConfig != null) {
          try {
            field.setAccessible(true);
            field.set(null, null);
            successCount += 1;
          } catch (Exception e) {
            Albatross.log("Reflection", e);
          }
        }
      }
    }

    boolean checkArgument = false;
    String methodName;
    MethodHookBackup hookBackup;
    StaticMethodHook staticMethodHook;
    MethodHook methodHook;
    StaticMethodHookBackup staticMethodHookBackup;
    ConstructorHook constructorHook;
    ConstructorHookBackup constructorHookBackup;
    MethodBackup methodBackup;
    StaticMethodBackup staticMethodBackup;
    ConstructorBackup constructorBackup;
    boolean needBackup = false;
    String[] aliases = null;
    int sdk = Build.VERSION.SDK_INT;
    boolean methodRequired;
    byte methodOption;
    byte callWay = CURRENT;
    int targetExec = DO_NOTHING;
    int hookerExec = DO_NOTHING;
    for (Method m : hooker.getDeclaredMethods()) {
      Annotation[] annotations = m.getAnnotations();
      if (annotations.length == 0) {
        continue;
      }
      byte minSdk;
      byte maxSdk;
      boolean targetStatic;
      Class<?> targetClass;
      String[] className;
      String[] classNameArgs;
      HookAction hookWay;
      ByName methodMarkAlias;
      if ((hookBackup = m.getAnnotation(MethodHookBackup.class)) != null) {
        targetStatic = hookBackup.isStatic();
        className = hookBackup.className();
        classNameArgs = hookBackup.value();
        needBackup = true;
        targetClass = hookBackup.targetClass();
        aliases = hookBackup.name();
        hookWay = HookAction.HOOK_METHOD;
        methodRequired = hookBackup.required();
        methodOption = hookBackup.option();
        minSdk = hookBackup.minSdk();
        maxSdk = hookBackup.maxSdk();
        callWay = hookBackup.callWay();
      } else if ((methodHook = m.getAnnotation(MethodHook.class)) != null) {
        targetStatic = methodHook.isStatic();
        targetClass = methodHook.targetClass();
        classNameArgs = methodHook.value();
        if ((methodBackup = m.getAnnotation(MethodBackup.class)) != null) {
          needBackup = true;
          callWay = methodBackup.callWay();
        } else
          needBackup = false;
        aliases = methodHook.name();
        className = methodHook.className();
        hookWay = HookAction.HOOK_METHOD;
        methodRequired = methodHook.required();
        methodOption = methodHook.option();
        minSdk = methodHook.minSdk();
        maxSdk = methodHook.maxSdk();
      } else if ((staticMethodHook = m.getAnnotation(StaticMethodHook.class)) != null) {
        targetStatic = true;
        targetClass = staticMethodHook.targetClass();
        classNameArgs = staticMethodHook.value();
        if ((staticMethodBackup = m.getAnnotation(StaticMethodBackup.class)) != null) {
          needBackup = true;
          callWay = staticMethodBackup.callWay();
        } else
          needBackup = false;
        className = staticMethodHook.className();
        aliases = staticMethodHook.name();
        hookWay = HookAction.HOOK_METHOD;
        methodRequired = staticMethodHook.required();
        methodOption = staticMethodHook.option();
        minSdk = staticMethodHook.minSdk();
        maxSdk = staticMethodHook.maxSdk();
      } else if ((staticMethodHookBackup = m.getAnnotation(StaticMethodHookBackup.class)) != null) {
        targetStatic = true;
        targetClass = staticMethodHookBackup.targetClass();
        classNameArgs = staticMethodHookBackup.value();
        needBackup = true;
        aliases = staticMethodHookBackup.name();
        hookWay = HookAction.HOOK_METHOD;
        className = staticMethodHookBackup.className();
        methodRequired = staticMethodHookBackup.required();
        methodOption = staticMethodHookBackup.option();
        minSdk = staticMethodHookBackup.minSdk();
        maxSdk = staticMethodHookBackup.maxSdk();
        callWay = staticMethodHookBackup.callWay();
      } else if ((constructorHook = m.getAnnotation(ConstructorHook.class)) != null) {
        hookWay = HookAction.HOOK_CONSTRUCTOR;
        targetStatic = false;
        targetClass = constructorHook.targetClass();
        classNameArgs = constructorHook.value();
        if ((constructorBackup = m.getAnnotation(ConstructorBackup.class)) != null) {
          needBackup = true;
          callWay = constructorBackup.callWay();
        } else
          needBackup = false;
        className = constructorHook.className();
        methodRequired = constructorHook.required();
        methodOption = constructorHook.option();
        minSdk = constructorHook.minSdk();
        maxSdk = constructorHook.maxSdk();
      } else if ((constructorHookBackup = m.getAnnotation(ConstructorHookBackup.class)) != null) {
        hookWay = HookAction.HOOK_CONSTRUCTOR;
        targetClass = constructorHookBackup.targetClass();
        classNameArgs = constructorHookBackup.value();
        className = constructorHookBackup.className();
        needBackup = true;
        targetStatic = false;
        methodRequired = constructorHookBackup.required();
        methodOption = constructorHookBackup.option();
        minSdk = constructorHookBackup.minSdk();
        maxSdk = constructorHookBackup.maxSdk();
        callWay = constructorHookBackup.callWay();
      } else if ((methodBackup = m.getAnnotation(MethodBackup.class)) != null) {
        targetStatic = methodBackup.isStatic();
        targetClass = methodBackup.targetClass();
        className = methodBackup.className();
        classNameArgs = methodBackup.value();
        hookWay = HookAction.BACKUP_METHOD;
        aliases = methodBackup.name();
        methodRequired = methodBackup.required();
        methodOption = methodBackup.option();
        minSdk = methodBackup.minSdk();
        maxSdk = methodBackup.maxSdk();
        callWay = methodBackup.callWay();
      } else if ((staticMethodBackup = m.getAnnotation(StaticMethodBackup.class)) != null) {
        targetStatic = true;
        targetClass = staticMethodBackup.targetClass();
        className = staticMethodBackup.className();
        classNameArgs = staticMethodBackup.value();
        aliases = staticMethodBackup.name();
        hookWay = HookAction.BACKUP_METHOD;
        methodRequired = staticMethodBackup.required();
        methodOption = staticMethodBackup.option();
        minSdk = staticMethodBackup.minSdk();
        maxSdk = staticMethodBackup.maxSdk();
        callWay = staticMethodBackup.callWay();
      } else if ((constructorBackup = m.getAnnotation(ConstructorBackup.class)) != null) {
        targetClass = constructorBackup.targetClass();
        classNameArgs = constructorBackup.value();
        className = constructorBackup.className();
        hookWay = HookAction.BACKUP_CONSTRUCTOR;
        targetStatic = false;
        methodRequired = constructorBackup.required();
        methodOption = constructorBackup.option();
        minSdk = constructorBackup.minSdk();
        maxSdk = constructorBackup.maxSdk();
        callWay = constructorBackup.callWay();
      } else {
        if (!Modifier.isStatic(m.getModifiers()))
          if (isMirror)
            throw new RedundantMethodErr(m);
        continue;
      }
      if (minSdk != 0) {
        if (sdk < minSdk)
          continue;
      }
      if (maxSdk != 0) {
        if (sdk > maxSdk)
          continue;
      }
      Class<?>[] mParameterTypes = m.getParameterTypes();
      Annotation[][] parameterAnnotations = m.getParameterAnnotations();
      boolean isHookStatic = Modifier.isStatic(m.getModifiers());
      if (targetClass == TargetClass.class) {
        try {
          targetClass = getTargetClass(defaultClass, className, targetStatic ? null : (isHookStatic ? mParameterTypes[0] : null), loader);
        } catch (ClassNotFoundException e) {
          continue;
        }
      }
      Class<?>[] argTypes;
      Method targetMethod = null;
      Constructor<?> targetConstructor = null;
      if (hookWay.v <= HookAction.BACKUP_METHOD.v && (methodMarkAlias = m.getAnnotation(ByName.class)) != null) {
        argTypes = null;
        try {
          if ((methodOption & DefOption.VIRTUAL) == 0)
            targetMethod = ReflectUtils.findDeclaredMethodByName(targetClass, methodMarkAlias.value(), methodMarkAlias.onlyAnno());
          else
            targetMethod = ReflectUtils.findMethodByName(targetClass, methodMarkAlias.value(), methodMarkAlias.onlyAnno());
          checkArgument = true;
        } catch (NoSuchMethodException ignore) {
        }
      } else {
        try {
          if (classNameArgs.length > 0) {
            argTypes = getArgumentTypesFromString(classNameArgs, loader, false);
            checkArgument = true;
          } else {
            checkArgument = false;
            argTypes = getArgumentTypes(dependencies, parameterAnnotations, targetClass, mParameterTypes, loader, targetStatic, isHookStatic);
          }
        } catch (ClassNotFoundException e) {
          if (methodRequired) {
            throw new RequiredMethodErr("required method argument class is not find:" + e.getMessage(), annotations[0]);
          }
          continue;
        } catch (FindMethodException e) {
          CheckParameterTypesResult subArgTypes = e.subArgTypes;
          if (!isMirror)
            subArgTypes.excludeMethod = m;
          if (hookWay.v <= HookAction.BACKUP_METHOD.v) {
            String name;
            if (hookWay == HookAction.HOOK_METHOD)
              name = getSplitValue(HOOK_SUFFIX, m.getName());
            else
              name = getSplitValue(BACKUP_SUFFIX, m.getName());
            if ((methodOption & DefOption.VIRTUAL) == 0) {
              targetMethod = ReflectUtils.findDeclaredMethodWithType(targetClass, name, e.argTypes, subArgTypes);
              if (targetMethod == null) {
                for (String alias : aliases) {
                  targetMethod = ReflectUtils.findDeclaredMethodWithType(targetClass, alias, e.argTypes, subArgTypes);
                  if (targetMethod != null)
                    break;
                }
              }
            } else {
              targetMethod = ReflectUtils.findMethodWithType(targetClass, name, e.argTypes, subArgTypes);
              if (targetMethod == null) {
                for (String alias : aliases) {
                  targetMethod = ReflectUtils.findMethodWithType(targetClass, alias, e.argTypes, subArgTypes);
                  if (targetMethod != null)
                    break;
                }
              }
            }
            if (targetMethod == null) {
              continue;
            }
          } else {
            targetConstructor = ReflectUtils.findDeclaredConstructorWithType(targetClass, e.argTypes, subArgTypes);
            if (targetConstructor == null) {
              continue;
            }
          }
          argTypes = e.argTypes;
        }
      }
      switch (hookWay) {
        case HOOK_METHOD:
        case BACKUP_METHOD: {
          if (targetMethod == null) {
            try {
              if (hookWay == HookAction.HOOK_METHOD)
                methodName = getSplitValue(HOOK_SUFFIX, m.getName());
              else
                methodName = getSplitValue(BACKUP_SUFFIX, m.getName());
              if ((methodOption & DefOption.VIRTUAL) == 0)
                targetMethod = targetClass.getDeclaredMethod(methodName, argTypes);
              else
                targetMethod = ReflectUtils.findMethod(targetClass, methodName, argTypes);
            } catch (Exception e) {
              for (String alias : aliases) {
                try {
                  if ((methodOption & DefOption.VIRTUAL) == 0)
                    targetMethod = targetClass.getDeclaredMethod(alias, argTypes);
                  else
                    targetMethod = ReflectUtils.findMethod(targetClass, alias, argTypes);
                  break;
                } catch (Exception ignore) {
                }
              }
            }
          }
          if (targetMethod != null) {
            try {
              checkMethodReturn(dependencies, targetMethod, m);
              if ((methodOption & DefOption.NOTHING) == 0) {
                if (hookWay == HookAction.HOOK_METHOD)
                  putHook(hookRecord, targetMethod, m, needBackup, targetExec, hookerExec).checkMethSign = checkArgument;
                else if (callWay == CURRENT)
                  putBackup(hookRecord, targetMethod, m, targetExec).checkMethSign = checkArgument;
                else {
                  putMirrorBackup(backupRecords, targetMethod, m, targetExec, callWay).checkMethSign = checkArgument;
                }
              }
            } catch (AlbatrossErr e) {
              throw e;
            } catch (Exception e) {
              log("Wrong target method for " + m, e);
              if (methodRequired) {
                throw new RequiredMethodErr("Wrong target method for " + m, annotations[0]);
              }
            }
          } else {
            log("Cannot find target method for " + m);
            if (methodRequired) {
              throw new RequiredMethodErr("Cannot find target method for " + m, annotations[0]);
            }
          }
          break;
        }
        case HOOK_CONSTRUCTOR:
        case BACKUP_CONSTRUCTOR: {
          if (targetConstructor == null) {
            try {
              targetConstructor = targetClass.getDeclaredConstructor(argTypes);
            } catch (NoSuchMethodException e) {
            }
          }
          if (targetConstructor != null) {
            try {
              checkMethodReturn(dependencies, targetConstructor, m);
              if ((methodOption & DefOption.NOTHING) == 0) {
                if (hookWay == HookAction.HOOK_CONSTRUCTOR)
                  putHook(hookRecord, targetConstructor, m, needBackup, targetExec, hookerExec).checkMethSign = checkArgument;
                else if (callWay == CURRENT)
                  putBackup(hookRecord, targetConstructor, m, targetExec).checkMethSign = checkArgument;
                else {
                  putMirrorBackup(backupRecords, targetMethod, m, targetExec, callWay).checkMethSign = checkArgument;
                }
              }
            } catch (AlbatrossErr e) {
              throw e;
            } catch (Exception e) {
            }
          }
          break;
        }
      }
    }
    String[] methodNames = new String[2];
    Collection<HookRecord> hookRecords = hookRecord.values();
    for (HookRecord hookMethod : hookRecords) {
      Method hook = hookMethod.hook;
      Method backup = hookMethod.backup;
      Member target = hookMethod.target;
      try {
        boolean result;
        if (hook != null) {
          result = Albatross.unhookNative(target, hook, backup) == 0;
        } else {
          result = Albatross.unBackupNative(backup, CURRENT) == 0;
        }
        if (result) {
          if (!slotMap.isEmpty()) {
            if (hook != null)
              methodNames[0] = hook.getName();
            if (backup != null)
              methodNames[1] = backup.getName();
            for (String name : methodNames) {
              if (name == null)
                continue;
              if (slotMap.containsKey(name)) {
                Object[] v = (Object[]) slotMap.get(name);
                Field field = (Field) v[0];
                field.set(null, null);
                successCount += 1;
                slotMap.remove(name);
              }
            }
          }
          successCount += 1;
        }
      } catch (Throwable t) {
        log("Failed to hook " + target + ": hook=" + hook + (backup == null ? "" : ", backup=" + backup), t);
      }
    }
    for (BackupRecord backupRecord : backupRecords) {
      Method backup = backupRecord.backup;
      Member target = backupRecord.target;
      try {
        boolean result;
        result = Albatross.unBackupNative(backup, backupRecord.backupWay) == 0;
        if (result) {
          if (!slotMap.isEmpty()) {
            String name = backup.getName();
            if (slotMap.containsKey(name)) {
              Object[] v = (Object[]) slotMap.get(name);
              Field field = (Field) v[0];
              field.set(null, null);
              successCount += 1;
              slotMap.remove(name);
            }
          }
          successCount += 1;
          log("Mirror " + target + ": backup=" + backup);
        }
      } catch (Throwable t) {
        log("Failed to mirror " + target + ": backup=" + backup, t);
      }
    }
    return successCount;
  }


  private static Map<Class<? extends ReflectionBase>, FieldConfig> fieldClsMap;
  private static Map<Class<?>, Class<?>> primBoxMap;

  static class FieldConfig {
    Constructor<? extends ReflectionBase> constructor;
    Constructor<? extends ReflectionBase> constructor_slot;
    Class<?> clz;
    boolean needClz;

    FieldConfig(Constructor<? extends ReflectionBase> constructor, Class<?> clz) {
      this.constructor = constructor;
      this.clz = clz;
      if (constructor.getGenericParameterTypes().length == 1)
        needClz = false;
      else
        needClz = true;
    }
  }

  private static void putFieldClass(Class<? extends ReflectionBase> fieldClz, boolean needClz, Class<?> clz) {
    try {
      Constructor<? extends ReflectionBase> constructor, constructor_slot = null;
      if (needClz) {
        constructor = fieldClz.getConstructor(Set.class, Class.class, Field.class);
        constructor_slot = fieldClz.getConstructor(Object.class);
      } else {
        constructor = fieldClz.getConstructor(Field.class);
      }
      FieldConfig fieldConfig = new FieldConfig(constructor, clz);
      fieldConfig.constructor_slot = constructor_slot;
      fieldClsMap.put(fieldClz, fieldConfig);
    } catch (NoSuchMethodException e) {
      Albatross.log("putField", e);
    }
  }

  private static void initField() {
    try {
      fieldClsMap = new ArrayMap<>();
      putFieldClass(BooleanFieldDef.class, false, boolean.class);
      putFieldClass(ByteFieldDef.class, false, byte.class);
      putFieldClass(CharFieldDef.class, false, char.class);
      putFieldClass(ShortFieldDef.class, false, short.class);
      putFieldClass(IntFieldDef.class, false, int.class);
      putFieldClass(LongFieldDef.class, false, long.class);
      putFieldClass(FloatFieldDef.class, false, float.class);
      putFieldClass(DoubleFieldDef.class, false, double.class);

      putFieldClass(StaticBoolFieldDef.class, false, boolean.class);
      putFieldClass(StaticByteFieldDef.class, false, byte.class);
      putFieldClass(StaticCharFieldDef.class, false, char.class);
      putFieldClass(StaticShortFieldDef.class, false, short.class);
      putFieldClass(StaticIntConstFieldDef.class, false, int.class);
      putFieldClass(StaticIntFieldDef.class, false, int.class);
      putFieldClass(StaticLongFieldDef.class, false, long.class);
      putFieldClass(StaticFloatFieldDef.class, false, float.class);
      putFieldClass(StaticDoubleFieldDef.class, false, double.class);

      putFieldClass(FieldDef.class, false, null);
      putFieldClass(StaticFieldDef.class, false, null);
      putFieldClass(ConstructorDef.class, true, null);
      putFieldClass(MethodDef.class, true, null);
      putFieldClass(StaticMethodDef.class, true, null);
      putFieldClass(VoidMethodDef.class, true, void.class);
      putFieldClass(BooleanMethodDef.class, true, boolean.class);
      putFieldClass(StaticVoidMethodDef.class, true, void.class);
    } catch (Exception e) {
      Albatross.log("initField", e);
    }
    primBoxMap = new ArrayMap<>();
    primBoxMap.put(void.class, Void.class);
    primBoxMap.put(boolean.class, Boolean.class);
    primBoxMap.put(char.class, Character.class);
    primBoxMap.put(byte.class, Byte.class);
    primBoxMap.put(short.class, Short.class);
    primBoxMap.put(int.class, Integer.class);
    primBoxMap.put(float.class, Float.class);
    primBoxMap.put(long.class, Long.class);
    primBoxMap.put(double.class, Double.class);
    try {
      Field field = _$.class.getDeclaredField("s1");
      Field field2 = _$.class.getDeclaredField("s2");
      int accessFlags = field.getModifiers();
      int fieldStatus = initFieldOffsetNative(field, field2, accessFlags, _$.class);
      albatross_flags &= ~(FLAG_FIELD_BACKUP_INSTANCE | FLAG_FIELD_BACKUP_STATIC);
      switch (fieldStatus) {
        case 0:
          albatross_flags |= FLAG_FIELD_BACKUP_BAN;
          break;
        case 1:
          albatross_flags |= FLAG_FIELD_BACKUP_INSTANCE;
          break;
        case 2:
          albatross_flags |= FLAG_FIELD_BACKUP_INSTANCE | FLAG_FIELD_BACKUP_STATIC;
          break;
      }
    } catch (Exception e) {
      Albatross.log("initFieldOffsetNative", e);
    }
  }


  public static native <T> T convert(Object object, Class<T> hooker);

  public static native boolean isMainThread();

  static class Address {
    private final long addr;

    public long getAddress() {
      return addr;
    }

    public Address(long addr) {
      this.addr = addr;
    }
  }

  public static class DlInfo {
    private long handle;

    public DlInfo(long handle) {
      this.handle = handle;
    }

    public long getSymbolAddress(String symbol) {
      if (handle > 0) {
        long symbolAddr = dlsym(handle, symbol);
        if (symbolAddr == 0)
          log("can not get symbol " + symbol);
        return symbolAddr;
      }
      return 0;
    }

    public void close() {
      if (handle > 0) {
        dlclose(handle);
        handle = 0;
      }
    }
  }

  public static DlInfo openLib(String libName) {
    long handle = dlopen(libName);
    if (handle > 40960)
      return new DlInfo(handle);
    return null;
  }

  private static native boolean markHooked(Class<?> clz);

  private static native boolean markUnhooked(Class<?> clz);

  public static native boolean isHooked(Class<?> clz);

  static Set<Class<?>> toVisitedClass;

  public static boolean addToVisit(Class<?> clz) {
    if (Build.VERSION.SDK_INT > 27) {
      if (clz != null) {
        if (!toVisitedClass.contains(clz)) {
          toVisitedClass.add(clz);
          if (!(clz.getClassLoader() instanceof BaseDexClassLoader))
            markTargetClass(clz);
          return true;
        }
      }
    }
    return false;
  }

  private static native boolean markTargetClass(Class<?> clz);

  public static synchronized native int transactionBegin(boolean disableHidden);

  public static int transactionBegin() {
    return transactionBegin(false);
  }

  public static int transactionEnd(boolean doTask) {
    return transactionEnd(doTask, (albatross_flags & FLAG_SUSPEND_VM) != 0);
  }

  public static int transactionEnd(boolean doTask, boolean suspendVM) {
    int ret;
    if ((ret = transactionEndNative(doTask, suspendVM)) == 0) {
      toVisitedClass.clear();
    }
    return ret;
  }

  private static synchronized native int transactionEndNative(boolean doTask, boolean suspendVM);

  public static synchronized native int transactionLevel();

  static native long dlopen(String libName);

  static native long dlsym(long obj, String symbol);

  static native void dlclose(long obj);

  //---------------------------------
  //final flag must exists
  private static final native int initFieldOffsetNative(Field field, Field field2, int accessFlags, Class<?> clz);

  private static final native int initMethodNative(Method initNativeMethod, Method method2, int initFlags, Class<?> clz);

  //---------------------------------

  private static native void registerMethodNative(Method ensureClassInitialized, Method onClassInit, Method appendLoaderMethod, Method compileCheck, Method onEnter, Class<?> stubClass);

  private static native void registerHookCallback(Method[] methods/*Method callVoid, Method callBool, Method callChar, Method callByte, Method callShort, Method callInt, Method callFloat,
                                                  Method callLong, Method callDouble, Method callObject*/);

  private static native void measureLayoutNative(Method method);

  private static final List<ClassLoader> classLoaderList = new ArrayList<>();

  @Alias("appendLoader")
  static boolean appendLoader(ClassLoader loader) {
    synchronized (classLoaderList) {
      if (classLoaderList.contains(loader))
        return false;
      ClassLoader classLoader = loader.getParent();
      if (classLoader == null && !(loader instanceof BaseDexClassLoader))
        return false;
      classLoaderList.add(loader);
    }
    return true;
  }

  @Alias("currentApplication")
  public static native Application currentApplication();

  @Alias("currentPackageName")
  public static native String currentPackageName();


  @Alias("currentProcessName")
  public native static String currentProcessName();


  @TargetClass(className = "android.app.ActivityThread")
  public static class ActivityThreadH {

    @MethodBackup
    private native Instrumentation getInstrumentation();

    @StaticMethodBackup
    public native static ActivityThreadH currentActivityThread();

    @StaticMethodBackup
    public native static String currentOpPackageName();

    @MethodBackup
    private native Handler getHandler();

    @MethodBackup
    private native String getProfileFilePath();

  }

  public static Instrumentation currentInstrumentation() {
    return ActivityThreadH.currentActivityThread().getInstrumentation();
  }

  public static Handler getMainHandler() {
    return ActivityThreadH.currentActivityThread().getHandler();
  }

  public static String getProfileFilePath() {
    return ActivityThreadH.currentActivityThread().getProfileFilePath();
  }


  public static boolean syncAppLoader() {
    appendLoader(Albatross.class.getClassLoader());
    Application application = currentApplication();
    if (application.getClass().getName().startsWith("android.app.")) {
      Context context = application.getBaseContext();
      return false;
    } else {
      ClassLoader classLoader = application.getClassLoader();
      return appendLoader(classLoader);
    }
  }

  private static native int syncClassLoader();

  public static Class<?> findClass(String[] className) {
    for (String clzName : className) {
      Class<?> clz = findClass(clzName);
      if (clz != null)
        return clz;
    }
    return null;
  }

  public static Class<?> findClass(String[] className, ClassLoader loader) {
    for (String clzName : className) {
      try {
        return loader.loadClass(clzName);
      } catch (ClassNotFoundException ignore) {
      }
    }
    return null;
  }

  public static Class<?> findClass(String className) {
    try {
      for (ClassLoader classLoader : classLoaderList) {
        try {
          return classLoader.loadClass(className);
        } catch (ClassNotFoundException ignore) {
        } catch (Throwable throwable) {
          Albatross.log("find class err：" + className, throwable);
        }
      }
    } catch (ConcurrentModificationException m) {
      for (ClassLoader classLoader : classLoaderList.toArray(new ClassLoader[0])) {
        try {
          return classLoader.loadClass(className);
        } catch (ClassNotFoundException ignore) {
          // Expected, continue to next loader
        } catch (Throwable throwable) {
          Albatross.log("find class err：" + className, throwable);
        }
      }
    }
    return null;
  }

  public static Class<?> findClassFromApplication(String className) {
    Application application = currentApplication();
    if (application != null) {
      try {
        return application.getClassLoader().loadClass(className);
      } catch (ClassNotFoundException e) {
      } catch (Throwable throwable) {
        Albatross.log("find class err：" + className, throwable);
      }
    } else {
      try {
        return Class.forName(className);
      } catch (ClassNotFoundException e) {
      }
    }
    return null;
  }

  public static native Method findMethod(Class<?> clz, Class<?>[] argTypes, int isStatic);

  public static native Method[] getDeclaredMethods(Class<?> clz, int isStatic);

  private native static boolean addPendingHookNative(String clsName, Class<?> hookClass);

  public native static long getObjectAddress(Object object);

  private static boolean addPendingHook(String clsName, Class<?> hookClass) {
    if (pendingMap.containsKey(clsName))
      return false;
    pendingMap.put(clsName, hookClass);
    addPendingHookNative(clsName, hookClass);
    return true;
  }

  static Map<String, Class<?>> pendingMap;


  @Alias("onClassInit")
  private static boolean onClassInit(Class<?> targetClass) {
    Class<?> hookClass = pendingMap.get(targetClass.getName());
    if (hookClass == null)
      return false;
    boolean result;
    try {
      int count = hookClass(hookClass, targetClass.getClassLoader(), targetClass, null);
      if (count > 0)
        result = true;
      else
        result = false;
    } catch (AlbatrossErr e) {
      result = false;
    }
    TargetClass annotation = hookClass.getAnnotation(TargetClass.class);
    for (String className : annotation.className()) {
      pendingMap.remove(className);
    }
    return result;
  }

}
