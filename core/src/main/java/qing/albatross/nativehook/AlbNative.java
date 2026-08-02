package qing.albatross.nativehook;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import qing.albatross.annotation.FuncBackup;
//import qing.albatross.annotation.FuncHook;
//import qing.albatross.annotation.FuncReplace;
import qing.albatross.annotation.Symbol;
import qing.albatross.annotation.TargetLibrary;
import qing.albatross.core.Albatross;

public class AlbNative {


  static native long dlopen(String libName);

  static native long dlsym(long obj, String symbol);

  static native long iterSymbol(long obj, Object data);

  public static native void watchFunc(String symbol, long func);

  static native void dlclose(long obj);

  static native void iterLib(Object callback);

  public static native void hookInit(String logPath);

  public static native boolean dumpNativeMethod(String outputPath);

  static native void registerLibraryCallbackNative(Object callback, String libName);

  static native long hookInstructionNative(long address, Object onEnter, Object onLeave, Object userdata);

  static native long backupMethodNative(long address, Object method, byte[] args, byte retType);

  static native boolean unBackupMethodNative(long address);

  static native boolean unHookInstructionNative(long address);

  static native long getNthArgumentNative(long invokeContext, int nth);

  static native void setNthArgumentNative(long invokeContext, int nth, long value);

  static native void setReturnResultNative(long invokeContext, long value);

  static native long getReturnResultNative(long invokeContext);

  static native Object readObject(long address, Class<?> c);

  static native boolean readableNative(long address);

  static native void freeAddress(long address);

  static native long mallocNative(int size);

  static native long callocNative(int size);

  public static native long memset(long address,int v,int size);

  static native byte[] readBytes(long address, int length);

  static native boolean writeBytes(long address, byte[] data);

  static native int[] readInts(long address, int length);

  static native boolean writeInts(long address, int[] data);

  static native boolean writeString(long address, String string);

  static native String readString(long address, int maxLen);


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
    if (handle > 40960 || handle < 0)
      return new DlInfo(handle);
    return null;
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
        field.set(null, address);
        successCount += 1;
        Albatross.log("find symbol " + field.getName() + " address:" + address);
      } catch (IllegalAccessException ignore) {
      }
    }
//    FuncBeforeCall funcBeforeCall;
    FuncBackup funcBackup;
//    FuncReplace funcReplace;
//    FuncAfterCall funcAfterCall;
//    FuncHook funcHook;
    HookAction hookWay = null;
    for (Method m : hooker.getDeclaredMethods()) {
      Annotation[] annotations = m.getAnnotations();
      if (annotations.length == 0)
        continue;
      int modifiers = m.getModifiers();
      if (!Modifier.isStatic(modifiers))
        continue;
      String[] names = null;
      /*if ((funcBeforeCall = m.getAnnotation(FuncBeforeCall.class)) != null) {

      } else if ((funcAfterCall = m.getAnnotation(FuncAfterCall.class)) != null) {

      } else */if ((funcBackup = m.getAnnotation(FuncBackup.class)) != null) {
        hookWay = HookAction.BACKUP_METHOD;
        names = funcBackup.value();
        if (!Modifier.isNative(modifiers)) {
          Albatross.log("backup method " + m + " must native");
          continue;
        }
      } /*else if ((funcReplace = m.getAnnotation(FuncReplace.class)) != null) {

      } else if ((funcHook = m.getAnnotation(FuncHook.class)) != null) {
        hookWay = HookAction.HOOK_METHOD;
      }*/ else {
        continue;
      }
      long targetAddress = 0;
      if (names == null || names.length == 0) {
        targetAddress = dlsym(address, m.getName());
        if (targetAddress == 0)
          continue;
      } else {
        for (String n : names) {
          targetAddress = dlsym(address, n);
          if (targetAddress != 0)
            break;
        }
      }
      if (targetAddress == 0) {
        Albatross.log("can not find address for:" + m);
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
      }
    }
    dlclose(address);
    return successCount;
  }

}
