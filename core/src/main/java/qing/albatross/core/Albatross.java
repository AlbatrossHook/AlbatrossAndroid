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


import static qing.albatross.annotation.ExecOption.AOT;
import static qing.albatross.annotation.ExecOption.DEFAULT_OPTION;
import static qing.albatross.annotation.ExecOption.DO_NOTHING;
import static qing.albatross.annotation.ExecOption.INTERPRETER;
import static qing.albatross.annotation.ExecOption.DECOMPILE;
import static qing.albatross.annotation.ExecOption.JIT_OPTIMIZED;
import static qing.albatross.annotation.ExecOption.NATIVE_CODE;
import static qing.albatross.annotation.ExecOption.RECOMPILE_OPTIMIZED;
import static qing.albatross.core.InstructionListener.hookInstructionNative;
import static qing.albatross.reflection.ReflectUtils.getArgumentTypesFromString;

import android.annotation.SuppressLint;
import android.app.Application;
import android.content.Context;
import android.os.Build;
import android.os.Debug;
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
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import dalvik.system.BaseDexClassLoader;
import qing.albatross.annotation.ArgumentTypeSlot;
import qing.albatross.annotation.ConstructorBackup;
import qing.albatross.annotation.ConstructorHook;
import qing.albatross.annotation.ConstructorHookBackup;
import qing.albatross.annotation.FieldRef;
import qing.albatross.annotation.MethodBackup;
import qing.albatross.annotation.DefOption;
import qing.albatross.annotation.MethodHook;
import qing.albatross.annotation.MethodHookBackup;
import qing.albatross.annotation.RunMode;
import qing.albatross.annotation.ParamInfo;
import qing.albatross.annotation.StaticMethodBackup;
import qing.albatross.annotation.StaticMethodHook;
import qing.albatross.annotation.StaticMethodHookBackup;
import qing.albatross.annotation.SubType;
import qing.albatross.annotation.TargetClass;
import qing.albatross.common.HookResult;
import qing.albatross.exception.AlbatrossErr;
import qing.albatross.exception.AlbatrossException;
import qing.albatross.exception.CheckParameterTypesResult;
import qing.albatross.exception.FieldException;
import qing.albatross.exception.FieldExceptionReason;
import qing.albatross.exception.FindMethodException;
import qing.albatross.exception.NotNativeBackupErr;
import qing.albatross.exception.RedundantFieldErr;
import qing.albatross.exception.RedundantMethodErr;
import qing.albatross.exception.MethodExceptionReason;
import qing.albatross.exception.HookerStructErr;
import qing.albatross.exception.RepetitiveBackupErr;
import qing.albatross.exception.RequiredClassErr;
import qing.albatross.exception.RequiredFieldErr;
import qing.albatross.exception.RequiredInstanceErr;
import qing.albatross.exception.RequiredMethodErr;
import qing.albatross.exception.VirtualCallBackupErr;
import qing.albatross.exception.MethodException;
import qing.albatross.reflection.BooleanFieldDef;
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


  public static final String HOOK_SUFFIX = "\\$Hook";
  public static final String BACKUP_SUFFIX = "\\$Backup";

  public final static int STATUS_INIT_OK = 1;
  public final static int STATUS_DISABLED = 2;
  public final static int STATUS_NOT_INIT = 4;
  public final static int STATUS_INIT_FAIL = 8;

  //note:these two fields must be placed right next to each other!
  public static int initStatus = STATUS_NOT_INIT;
  public static boolean initClass = false;

  public static boolean loaderFromCaller = false;

  public static Class<?> getHookerTargetClass(Class<?> hooker) {
    TargetClass targetClass = hooker.getAnnotation(TargetClass.class);
    if (targetClass != null) {
      return getTargetClassFromAnnotation(targetClass, null);
    }
    return null;
  }

  private static boolean checkTargetClass(Set<Class<?>> dependencies, Class<?> hooker, Class<?> expectClass) throws AlbatrossErr {
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
      if (addAssignableHooker(hooker, t)) {
        dependencies.add(hooker);
      }
      return true;
    }
    return false;
  }

  private static Set<Class<?>> checkMethodReturn(Set<Class<?>> dependencies, Object original, Method replacement) throws AlbatrossException {
    Class<?> returnType = replacement.getReturnType();
    if (original instanceof Method) {
      Class<?> originalReturn = ((Method) original).getReturnType();
      if (!returnType.isAssignableFrom(originalReturn)) {
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

  public static final int kArm = 1;
  public static final int kArm64 = 2;
  public static final int kX86 = 3;
  public static final int kX86_64 = 4;

  public static native int getRuntimeISA();

  public native static boolean initRpcClass(Class<?> clz);

  public static boolean addAssignableHooker(Class<?> hooker, Class<?> targetClass) throws AlbatrossErr {
    int setResult = setHookerAssignableNative(hooker, targetClass);
    if (setResult > 0) {
      __hookClass(hooker, null, targetClass, null);
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

  public static boolean replace(Member target, Method hook) throws AlbatrossException {
    return backupAndHook(target, hook, null, true, true, null, DO_NOTHING, DO_NOTHING);
  }

  public static boolean backupAndHook(Member target, Method hook, Method backup) throws AlbatrossException {
    return backupAndHook(target, hook, backup, true, true, null, DO_NOTHING, DO_NOTHING);
  }

  private static int hookDependency(Set<Class<?>> dependencies) throws HookerStructErr {
    return 0;
  }

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
    boolean doTransaction = dependencies == null && !currentDependencies.isEmpty();
    if (doTransaction) {
      transactionBegin();
    }
    int result = backupAndHookNative(target, hook, backup, targetExecMode, hookerExecMode);
    if (doTransaction) {
      hookDependency(currentDependencies);
      transactionEnd(true);
    }
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
    return backup(target, backup, true, true, null, DO_NOTHING);
  }

  public static boolean backup(Member target, Method backup, boolean check, boolean checkReturn, Set<Class<?>> dependencies, int execMode) throws AlbatrossException {
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
    boolean doTransaction = dependencies == null && !currentDependencies.isEmpty();
    if (doTransaction) {
      transactionBegin();
    }
    int result = backupNative(target, backup, execMode);
    if (doTransaction) {
      hookDependency(currentDependencies);
      transactionEnd(true);
    }
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

  private synchronized static native int unHookNative(Object target, Method hook, Method backup);

  private synchronized static native int backupAndHookNative(Object target, Method hook, Method backup, int targetExecMode, int hookerExecMode);


  public static InstructionListener hookInstruction(Member member, int minDexPc, int maxDexPc, InstructionCallback callback) {
    InstructionListener listener = new InstructionListener();
    if (Modifier.isStatic(member.getModifiers())) {
      ensureClassInitialized(member.getDeclaringClass());
    }
    long listenerId = hookInstructionNative(member, minDexPc, maxDexPc, listener);
    if (listenerId > 4096) {
      listener.listenerId = listenerId;
      listener.callback = callback;
      listener.member = member;
      return listener;
    }
    return null;
  }


  private synchronized static native int unBackupNative(Method backup);

  private synchronized static native int backupNative(Object target, Method backup, int execMode);

  public synchronized static native int backupFieldNative(Field target, Field backup);


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
      } else {
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
  public static final int FLAG_COMPILE = 0x8;
  public static final int FLAG_SUSPEND_VM = 0x10;
  public static final int FLAG_NO_COMPILE = 0x20;


  public static final int FLAG_FIELD_BACKUP_INSTANCE = 0x40;
  public static final int FLAG_FIELD_BACKUP_STATIC = 0x80;
  public static final int FLAG_FIELD_BACKUP_BAN = 0x100;
  public static final int FLAG_FIELD_DISABLED = 0x200;
  public static final int FLAG_FIELD_INVALID = FLAG_FIELD_BACKUP_BAN | FLAG_FIELD_DISABLED;

  public static final int FLAG_DISABLE_LOG = 0x400;


  private static int albatross_flags = 0;

  public static boolean loadLibrary(String library, int loadFlags) {
    if ((initStatus & STATUS_INIT_OK) != STATUS_INIT_OK) {
      if ((loadFlags & FLAG_DEBUG) != 0) {
        Debug.waitForDebugger();
      }
      try {
        getRuntimeISA();
      } catch (Throwable e) {
        System.loadLibrary(library);
        getRuntimeISA();
      }
      return init(loadFlags);
    }
    return false;
  }

  private native static boolean ensureClassInitializedNative(Class<?> clz);

  private native static void banInstance(Class<?> clz);

  public static boolean ensureClassInitialized(Class<?> clazz) {
    try {
      Class.forName(clazz.getName(), true, clazz.getClassLoader());
      return true;
    } catch (Throwable ignored) {
    }
    return false;
  }

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

  @SuppressLint({"BlockedPrivateApi", "SoonBlockedPrivateApi"})
  public static boolean init(int flags) {
    if ((initStatus & STATUS_INIT_OK) == STATUS_INIT_OK)
      return true;
    albatross_flags = flags;
    try {
      if (containsFlags(FLAG_DEBUG)) {
        Debug.waitForDebugger();
      }
      Method initNativeMethod = Albatross.class.getDeclaredMethod("initMethodNative", Method.class, Method.class, int.class, Class.class);
      int initResult = initMethodNative(initNativeMethod, Albatross.class.getDeclaredMethod("initFieldOffsetNative", Field.class, Field.class, int.class, Class.class), initNativeMethod.getModifiers(), Albatross.class);
      if ((initResult & 1) != 0) {
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
        transactionBegin(false);
        try {
          Method ensureClassInitialized = Albatross.class.getDeclaredMethod("ensureClassInitialized", Class.class);
          if ((initResult & 4) != 0) {
            Method ensureClassInitializedVisibly = Albatross.class.getDeclaredMethod("ensureClassInitializedForVisibly", Class.class);
            replace(ensureClassInitialized, ensureClassInitializedVisibly);
            ensureClassInitialized = ensureClassInitializedVisibly;
          }
          registerMethodNative(ensureClassInitialized, Albatross.class.getDeclaredMethod("onClassInit", Class.class),
              Albatross.class.getDeclaredMethod("appendLoader", ClassLoader.class), Albatross.class.getDeclaredMethod("checkMethodReturn", Set.class, Object.class, Method.class),
              InstructionListener.class.getDeclaredMethod("onEnter", Object.class, int.class, long.class));
          int sdkInt = Build.VERSION.SDK_INT;
          if (sdkInt > 28 && sdkInt < 35) {
            Class<?> Reflection = Class.forName("sun.reflect.Reflection");
            addToVisit(Reflection);
            Albatross.backup(Reflection.getDeclaredMethod("getCallerClass"), Albatross.class.getDeclaredMethod("getCallerClass"), false, false, null, AOT);
          } else {
            Class<?> VMStack = Class.forName("dalvik.system.VMStack");
            addToVisit(VMStack);
            Albatross.backup(VMStack.getDeclaredMethod("getStackClass1"), Albatross.class.getDeclaredMethod("getCallerClass"), false, false, null, AOT);
          }
          Class<?> ActivityThread = Class.forName("android.app.ActivityThread");
          addToVisit(ActivityThread);
          Albatross.backup(ActivityThread.getDeclaredMethod("currentApplication"), Albatross.class.getDeclaredMethod("currentApplication"), false, false, null, AOT);
          defaultHookerBackupExecMode = INTERPRETER;
          if (Debug.isDebuggerConnected() || containsFlags(FLAG_NO_COMPILE)) {
            albatross_flags |= FLAG_NO_COMPILE;
            if (sdkInt <= 25) {
              disableFieldBackup();
            }
            defaultHookerExecMode = DO_NOTHING;
            defaultTargetExecMode = DO_NOTHING;
          } else {
            if ((initResult & 8) != 0) {
              defaultHookerExecMode = JIT_OPTIMIZED | AOT;
              defaultTargetExecMode = JIT_OPTIMIZED | AOT;
            } else {
              defaultHookerExecMode = JIT_OPTIMIZED;
              defaultTargetExecMode = JIT_OPTIMIZED;
            }
            if (sdkInt <= 25) {
              disableFieldBackup();
            } else {
              if ((initResult & 2) == 2)
                defaultHookerBackupExecMode = RECOMPILE_OPTIMIZED;
            }
          }
          if (!containsFlags(FLAG_NO_COMPILE)) {
            if (compileMethod(Albatross.class.getDeclaredMethod("backup", Member.class, Method.class, boolean.class, boolean.class, Set.class, int.class))) {
              compileMethod(Albatross.class.getDeclaredMethod("backupAndHook", Member.class, Method.class, Method.class, boolean.class, boolean.class, Set.class, int.class, int.class));
              compileMethod(Albatross.class.getDeclaredMethod("__hookClass", Class.class, ClassLoader.class, Class.class, Object.class));
              compileMethod(Albatross.class.getDeclaredMethod("backupField", Set.class, Field.class, Field.class, Class.class));
            }
          }
          if (containsFlags(FLAG_DISABLE_LOG)) {
            albatross_flags &= ~FLAG_DISABLE_LOG;
            disableLog();
          }
          initClassLoader();
        } finally {
          transactionEnd(true);
        }
        pendingMap = new HashMap<>();

        return true;
      } else {
        initStatus |= STATUS_INIT_FAIL | FLAG_FIELD_BACKUP_BAN;
      }
    } catch (Exception e) {
      Albatross.log("Albatross init", e);
    }
    return false;
  }

  private static void initClassLoader() throws AlbatrossException {
    syncClassLoader();
    Albatross.__hookClass(ClassLoaderHook.class, ClassLoader.class.getClassLoader(), ClassLoader.class, null);
  }

  public static void log(String msg) {
    Log.i(TAG, msg);
  }

  public static void log(String msg, Throwable tr) {
    Log.e(TAG, msg, tr);
  }

  public static HookRecord putHook(Set<Class<?>> dependencies, HashMap<Object, HookRecord> hookRecord, Member target, Method hook, boolean isBackup, int targetExecMode, int hookerExecMode) throws AlbatrossException {
    HookRecord hookMethod;
    checkMethodReturn(dependencies, target, hook);
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

  private static HookRecord putBackup(Set<Class<?>> dependencies, HashMap<Object, HookRecord> hookRecord, Member target, Method backup, int targetExecMode) throws AlbatrossException {
    HookRecord hookMethod;
    checkMethodReturn(dependencies, target, backup);
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

  private static String getSplitValue(String split, String name) {
    String[] names = name.split(split);
    return names[0];
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
    } catch (Exception e) {
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
      res = __hookClass(hooker, loader, defaultClass, instance);
      doTask = true;
    } finally {
      transactionEnd(doTask);
    }
    return res;
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
          if (addAssignableHooker(clz, realClz))
            dependencies.add(clz);
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
          } else if (annotation instanceof SubType) {
            if (mParameterSubTypes == null)
              mParameterSubTypes = new Class<?>[mParameterTypes.length];
            mParameterSubTypes[i] = clz;
          }
        }
      }
    }
    if (mParameterSubTypes == null)
      return null;
    CheckParameterTypesResult result = new CheckParameterTypesResult();
    result.mParameterSubTypes = mParameterSubTypes;
    result.hookerClasses = hookerClasses;
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

  public static final int CLASS_ALREADY_HOOK = -1000000000;
  public static final int REDUNDANT_ELEMENT = -1000000001;

  public static void disableAlbatross() {
    initStatus |= STATUS_DISABLED;
  }

  public static void disableFieldBackup() {
    albatross_flags |= FLAG_FIELD_DISABLED;
  }

  public static void enableAlbatross() {
    initStatus &= ~STATUS_DISABLED;
  }

  private static int defaultHookerExecMode;
  private static int defaultHookerBackupExecMode;
  private static int defaultTargetExecMode;

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


  private static int __hookClass(Class<?> hooker, ClassLoader loader, Class<?> defaultClass, Object instance) throws AlbatrossErr {
    if (initStatus > STATUS_INIT_OK) {
      return 0;
    }
    if (!markHooked(hooker)) {
      if (defaultClass == null || (!pendingMap.containsKey(defaultClass.getName())))
        return CLASS_ALREADY_HOOK;
    }
    HashMap<Object, HookRecord> hookRecord = new HashMap<>();
    try {
      if (!isHookerAssignableNative(hooker, null))
        ensureClassInitialized(hooker);
      banInstance(hooker);
    } catch (Exception e) {
      throw new RuntimeException("init hook class fail", e);
    }
    int hookerDefaultExecOption = defaultHookerExecMode;
    int hookerBackupDefaultExecOption = defaultHookerBackupExecMode;
    int targetDefaultExecOption = defaultTargetExecMode;
    boolean isDebug = Debug.isDebuggerConnected();
    if (defaultClass == null) {
      TargetClass targetClass = hooker.getAnnotation(TargetClass.class);
      if (targetClass != null) {
        if (!isDebug) {
          int hookerExecOption = targetClass.hookerExec();
          int targetExecOption = targetClass.targetExec();
          int hookerBackupExecOption = targetClass.hookerBackupExec();
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
        defaultClass = getTargetClassFromAnnotation(targetClass, loader);
        if (defaultClass == TargetClass.class)
          defaultClass = null;
        if (defaultClass != null) {
          if (loader == null)
            loader = defaultClass.getClassLoader();
        } else if (targetClass.pendingHook()) {
          String[] classNames = targetClass.className();
          for (String className : classNames) {
            addPendingHook(className, hooker);
          }
          return 0;
        } else if (targetClass.required()) {
          throw new RequiredClassErr(targetClass);
        }
      }
    } else {
      if (loader == null) {
        loader = defaultClass.getClassLoader();
      }
    }
    if (defaultClass != null) {
      if (!(defaultClass.getClassLoader() instanceof BaseDexClassLoader)) {
        addToVisit(defaultClass);
      }
    }
    int runModeAnnotationCount = 0;
    int success_count = 0;
    Field[] fields = hooker.getDeclaredFields();
    Set<Class<?>> dependencies = new HashSet<>();
    Map<String, Object> slotMap = new HashMap<>();
    boolean fieldEnable = !containsFlags(FLAG_FIELD_INVALID);
    boolean staticEnable = containsFlags(FLAG_FIELD_BACKUP_STATIC);
    for (Field field : fields) {
      if (field == null)
        continue;
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
              success_count += 1;
              hookerDefaultExecOption &= ~AOT;
              hookerDefaultExecOption |= INTERPRETER;
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
        if (field.getAnnotation(FieldRef.class) == null)
          throw new RedundantFieldErr(field);
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
              if ((!expectClass.isAssignableFrom(realType)) && !(realType == void.class && expectClass == Void.class)) {
                if (!checkTargetClass(dependencies, expectClass, realType)) {
                  log(String.format("wrong filed %s type,expect %s get %s", field.getName(), expectClass.getName(), realType.getName()));
                  continue;
                }
              }
            }
            field.set(null, newFieldDef);
          }
          success_count += 1;
        } catch (Exception e) {
          e.printStackTrace();
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
    int methodOption;
    for (Method m : hooker.getDeclaredMethods()) {
      Annotation[] annotations = m.getAnnotations();
      if (annotations.length == 0) {
        if (!Modifier.isStatic(m.getModifiers()))
          throw new RedundantMethodErr(m);
        continue;
      }
      int minSdk;
      int maxSdk;
      boolean targetStatic;
      Class<?> targetClass;
      String[] className;
      String[] args;
      int hookWay;
      int targetExec = CLASS_ALREADY_HOOK;
      if ((hookBackup = m.getAnnotation(MethodHookBackup.class)) != null) {
        targetStatic = hookBackup.isStatic();
        className = hookBackup.className();
        args = hookBackup.value();
        needBackup = true;
        targetClass = hookBackup.targetClass();
        aliases = hookBackup.name();
        hookWay = 1;
        hookerExec = hookBackup.hookerExec();
        targetExec = hookBackup.targetExec();
        methodRequired = hookBackup.required();
        methodOption = hookBackup.option();
        minSdk = hookBackup.minSdk();
        maxSdk = hookBackup.maxSdk();
      } else if ((methodHook = m.getAnnotation(MethodHook.class)) != null) {
        targetStatic = methodHook.isStatic();
        targetClass = methodHook.targetClass();
        args = methodHook.value();
        hookerExec = methodHook.hookerExec();
        if ((methodBackup = m.getAnnotation(MethodBackup.class)) != null) {
          needBackup = true;
          targetExec = methodBackup.targetExec();
        } else
          needBackup = false;
        aliases = methodHook.name();
        className = methodHook.className();
        hookWay = 1;
        methodRequired = methodHook.required();
        methodOption = methodHook.option();
        minSdk = methodHook.minSdk();
        maxSdk = methodHook.maxSdk();
      } else if ((staticMethodHook = m.getAnnotation(StaticMethodHook.class)) != null) {
        targetStatic = true;
        targetClass = staticMethodHook.targetClass();
        args = staticMethodHook.value();
        hookerExec = staticMethodHook.hookerExec();
        if ((staticMethodBackup = m.getAnnotation(StaticMethodBackup.class)) != null) {
          targetExec = staticMethodBackup.targetExec();
          needBackup = true;
        } else
          needBackup = false;
        className = staticMethodHook.className();
        aliases = staticMethodHook.name();
        hookWay = 1;
        methodRequired = staticMethodHook.required();
        methodOption = staticMethodHook.option();
        minSdk = staticMethodHook.minSdk();
        maxSdk = staticMethodHook.maxSdk();
      } else if ((staticMethodHookBackup = m.getAnnotation(StaticMethodHookBackup.class)) != null) {
        targetStatic = true;
        targetClass = staticMethodHookBackup.targetClass();
        args = staticMethodHookBackup.value();
        needBackup = true;
        aliases = staticMethodHookBackup.name();
        hookWay = 1;
        className = staticMethodHookBackup.className();
        hookerExec = staticMethodHookBackup.hookerExec();
        targetExec = staticMethodHookBackup.targetExec();
        methodRequired = staticMethodHookBackup.required();
        methodOption = staticMethodHookBackup.option();
        minSdk = staticMethodHookBackup.minSdk();
        maxSdk = staticMethodHookBackup.maxSdk();
      } else if ((constructorHook = m.getAnnotation(ConstructorHook.class)) != null) {
        hookWay = 2;
        targetStatic = false;
        targetClass = constructorHook.targetClass();
        args = constructorHook.value();
        hookerExec = constructorHook.hookerExec();
        if ((constructorBackup = m.getAnnotation(ConstructorBackup.class)) != null) {
          targetExec = constructorBackup.targetExec();
          needBackup = true;
        } else
          needBackup = false;
        className = constructorHook.className();
        methodRequired = constructorHook.required();
        methodOption = constructorHook.option();
        minSdk = constructorHook.minSdk();
        maxSdk = constructorHook.maxSdk();
      } else if ((constructorHookBackup = m.getAnnotation(ConstructorHookBackup.class)) != null) {
        hookWay = 2;
        targetClass = constructorHookBackup.targetClass();
        args = constructorHookBackup.value();
        className = constructorHookBackup.className();
        needBackup = true;
        targetStatic = false;
        hookerExec = constructorHookBackup.hookerExec();
        targetExec = constructorHookBackup.targetExec();
        methodRequired = constructorHookBackup.required();
        methodOption = constructorHookBackup.option();
        minSdk = constructorHookBackup.minSdk();
        maxSdk = constructorHookBackup.maxSdk();
      } else if ((methodBackup = m.getAnnotation(MethodBackup.class)) != null) {
        targetStatic = methodBackup.isStatic();
        targetClass = methodBackup.targetClass();
        className = methodBackup.className();
        args = methodBackup.value();
        hookWay = 3;
        aliases = methodBackup.name();
        targetExec = methodBackup.targetExec();
        methodRequired = methodBackup.required();
        methodOption = methodBackup.option();
        minSdk = methodBackup.minSdk();
        maxSdk = methodBackup.maxSdk();
      } else if ((staticMethodBackup = m.getAnnotation(StaticMethodBackup.class)) != null) {
        targetStatic = true;
        targetClass = staticMethodBackup.targetClass();
        className = staticMethodBackup.className();
        args = staticMethodBackup.value();
        aliases = staticMethodBackup.name();
        hookWay = 3;
        targetExec = staticMethodBackup.targetExec();
        methodRequired = staticMethodBackup.required();
        methodOption = staticMethodBackup.option();
        minSdk = staticMethodBackup.minSdk();
        maxSdk = staticMethodBackup.maxSdk();
      } else if ((constructorBackup = m.getAnnotation(ConstructorBackup.class)) != null) {
        targetClass = constructorBackup.targetClass();
        args = constructorBackup.value();
        className = constructorBackup.className();
        hookWay = 4;
        targetStatic = false;
        targetExec = constructorBackup.targetExec();
        methodRequired = constructorBackup.required();
        methodOption = constructorBackup.option();
        minSdk = constructorBackup.minSdk();
        maxSdk = constructorBackup.maxSdk();
      } else {
        if (!Modifier.isStatic(m.getModifiers()))
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
      try {
        if (args.length > 0) {
          argTypes = getArgumentTypesFromString(args, loader, false);
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
        if (hookWay == 1 || hookWay == 3) {
          String name;
          if (hookWay == 1)
            name = getSplitValue(HOOK_SUFFIX, m.getName());
          else
            name = getSplitValue(BACKUP_SUFFIX, m.getName());
          if ((methodOption & DefOption.VIRTUAL) == 0) {
            targetMethod = ReflectUtils.findDeclaredMethodWithType(targetClass, name, e.argTypes, e.subArgTypes);
            if (targetMethod == null) {
              for (String alias : aliases) {
                targetMethod = ReflectUtils.findDeclaredMethodWithType(targetClass, alias, e.argTypes, e.subArgTypes);
                if (targetMethod != null)
                  break;
              }
            }
          } else {
            targetMethod = ReflectUtils.findMethodWithType(targetClass, name, e.argTypes, e.subArgTypes);
            if (targetMethod == null) {
              for (String alias : aliases) {
                targetMethod = ReflectUtils.findMethodWithType(targetClass, alias, e.argTypes, e.subArgTypes);
                if (targetMethod != null)
                  break;
              }
            }
          }
          if (targetMethod == null) {
            log("Cannot find target method argument for " + m, e);
            if (methodRequired) {
              throw new RequiredMethodErr("required method argument class is not find:" + e.getMessage(), annotations[0]);
            }
            continue;
          }
        } else {
          targetConstructor = ReflectUtils.findDeclaredConstructorWithType(targetClass, e.argTypes, e.subArgTypes);
          if (targetConstructor == null) {
            log("Cannot find target constructor  for " + m, e);
            if (methodRequired) {
              throw new RequiredMethodErr("required target constructor is not find:" + e.getMessage(), annotations[0]);
            }
            continue;
          }
        }
        argTypes = e.argTypes;
      }
      switch (hookWay) {
        case 1:
        case 3: {
          if (targetMethod == null) {
            try {
              if (hookWay == 1)
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
              if ((methodOption & DefOption.NOTHING) == 0) {
                HookRecord hookMethod;
                if (hookWay == 1)
                  hookMethod = putHook(dependencies, hookRecord, targetMethod, m, needBackup, targetExec, hookerExec);
                else
                  hookMethod = putBackup(dependencies, hookRecord, targetMethod, m, targetExec);
                hookMethod.checkMethSign = checkArgument;
              } else {
                checkMethodReturn(dependencies, targetMethod, m);
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
        case 2:
        case 4: {
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
              if ((methodOption & DefOption.NOTHING) == 0) {
                HookRecord hookMethod;
                if (hookWay == 2)
                  hookMethod = putHook(dependencies, hookRecord, targetConstructor, m, needBackup, targetExec, hookerExec);
                else
                  hookMethod = putBackup(dependencies, hookRecord, targetConstructor, m, targetExec);
                hookMethod.checkMethSign = checkArgument;
              } else {
                checkMethodReturn(dependencies, targetConstructor, m);
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
    for (HookRecord hookMethod : hookRecord.values()) {
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
          result = Albatross.backup(target, backup, hookMethod.checkMethSign, false, dependencies, hookMethod.targetExec);
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
                  success_count += 1;
                }
                slotMap.remove(name);
              }
            }
          }
          success_count += 1;
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
      success_count += hookDependency(dependencies);
    }
    return success_count;
  }

  private static Map<Class<? extends ReflectionBase>, FieldConfig> fieldClsMap;

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
    } catch (NoSuchMethodException ignore) {
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
      putFieldClass(StaticVoidMethodDef.class, true, void.class);
    } catch (Exception e) {
      Albatross.log("initField", e);
    }
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

  private static final native int initMethodNative(Method initNativeMethod, Method method2, int accessFlags, Class<?> clz);

  private static native void registerMethodNative(Method ensureClassInitialized, Method onClassInit, Method appendLoaderMethod, Method compileCheck, Method onEnter);

  //---------------------------------

  public static final String TAG = Albatross.class.getSimpleName();

  private static List<ClassLoader> classLoaderList = new ArrayList<>();

  synchronized static boolean appendLoader(ClassLoader loader) {
    if (classLoaderList.contains(loader))
      return false;
    ClassLoader classLoader = loader.getParent();
    if (classLoader == null && !(loader instanceof BaseDexClassLoader))
      return false;
    classLoaderList.add(loader);
    return true;
  }

  public static native Application currentApplication();

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
    for (ClassLoader classLoader : classLoaderList) {
      try {
        return classLoader.loadClass(className);
      } catch (ClassNotFoundException e) {
      }
    }
    return null;
  }

  public static final int SEARCH_STATIC = 1;
  public static final int SEARCH_INSTANCE = 2;
  public static final int SEARCH_ALL = SEARCH_STATIC | SEARCH_INSTANCE;

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
