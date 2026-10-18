# Albatross Android - Hook And Reflection Framework for Android

[中文版本](README_CN.md)
----------------

## Overview
**Albatross Android** is a high-performance, low-impact hooking and reflection framework designed for Android systems (Android 7.0+, API 24+; the 4.0.0 release is tested through Android 17 / API 37, including 16 KB page-size devices). It is part of the broader **Albatross** ecosystem (including Albatross Server, Core, Manager, etc.), originally named after a nostalgic VR project from the developer's university days.

The framework enables method hook/field access through **Hooker classes (mirror classes)** that declaratively describe targets. The system automatically deduces target methods/fields and allows seamless interaction with hooked classes. Unlike traditional reflection-based approaches, it eliminates performance overhead while maintaining safety and compatibility.

---



## Design Principles

Built upon the YAHFA foundation with significant enhancements, this framework provides robust hooking capabilities while maintaining system stability and application performance.


Albatross adheres to the following design goals:

1. **Efficiency**: Prioritizes execution speed and resource conservation,Keep profile generation and method inlining (except for hooked methods).
2. **Code Elegance**: Maintains clean, maintainable, and well-documented code.
3. **Minimal System Impact**: No premature class initialization,respects non-public API policies,and not suspend VM.

## Key Features

###  Core Capabilities
- **Mirror Class Hooking**: Build hookers (mirror classes) to intercept methods and access fields
- **Transparent Conversion**: Seamlessly convert target classes to hookers
- **Batch Operations**: Transaction-based hooking with automatic dependency resolution
- **Zero-Reflection Design**: Direct method invocation and field access without reflection overhead
- **Pending Hooks**: Automatically hooks classes when new DEX load.
- **Native Hook**: Hook C/C++ functions in native libraries (`qing.albatross.nativehook`, new in 3.6.0; extended in 4.0.0 with delayed hooks, CModule/NativeFunction and Stalker).
- **Dynamic Java Hooks**: `Albatross.hookMethod(Member, MethodHookCallback)` installs before/after hooks on arbitrary methods without pre-generated mirror classes.
- **In-Process C Modules**: `CModule` compiles C with tcc at runtime; `NativeFunction` calls compiled or arbitrary native addresses with a declared signature.
- **Code Tracing**: `Stalker` exposes instruction-level tracing with call/ret/exec/block/compile events, transformers and register-level callouts.
- **Java Method Tracing**: the `ocean` module (`OceanTracker`/`OceanSinker`) traces calls, returns and field access, with keyword/string/default sinkers.
- **Async RPC**: `@AsyncRpc` moves long-running RPC methods off the agent's epoll loop onto worker threads.
- **16 KB Page Support**: native libraries are aligned for Android 15+ 16 KB page-size devices.
- **Multi-Use**: Functions as both hooking library and high-performance reflection alternative.
- **Easy Deployment**: Mirror-class  simplifies complex hooking logic

###  Platform Support
- **Android Versions**:
    - Tested in 4.0.0: API 29, 30, 34, 35 and 37 (x86, x86_64, arm64; including 16 KB page-size devices); API 24-28 use the same compatibility paths
    - Full support: API 26-37 (8.0-17)
    - Instruction hook: API 24-37 (7.0-17)
    - Method hook: API 24-37 (7.0-17)
    - Field access: API 26-37 (8.0-17)
    - ❌ Unsupported: API 23 and below (6.0 Marshmallow and earlier)
    - Integration: It is necessary to ensure that the target application process does not have an Lsposed environment. When using frida injection, the java environment of frida needs to be cleared
- **Architectures**: x86, x86_64, ARM, ARM64

###  Security & Stability
- No premature class initialization
- Respects non-public API restrictions while enabling hidden method/field access
- Maintains profile generation and inline compilation (target methods excluded from inlining)
- Maintains PGO/inlining for unmodified code
- Debug/Release mode optimization:
    - Debug: Stable execution with reduced performance optimization
    - Release: Aggressive JIT compilation for both hookers and targets


---

## Why Albatross?

| Feature             | Traditional Frameworks                  | Albatross                                 |
|---------------------|-----------------------------------------|-------------------------------------------|
| Initialization      | Often triggers classes                  | Zero                                      |
| Performance         | Reflection overhead                     | Native machine code speed                 |
| System Impact       | Disables Profield/Inlining              | Preserves compiler optimizations          |
| Safety              | Often bypasses API restrictions         | Respects non-public API policies          |
| Batch Hooking       | Cannot atomize hook class and its dependencies                                  | Automatic(Active dependent hooker)        
| Pending Hooking     | Not support(lazy initialization is not) | Trigger once target class is initialization |
| Instruction Hooking | Not support | Support                                   |
---

## Project Structure

###  `annotation`
This module  contains annotations used throughout the project. Annotations can be used to provide metadata about classes, methods, or fields, which can be processed at runtime.

### `core`
The core module provides the fundamental functionality of the Albatross Android framework. It contains the `Albatross` class, which offers various hooking - related methods such as method hooking, backup, and field backup.

### `server`
RPC server/client implementation, including `@AsyncRpc` dispatch for long-running methods.

### `ocean`
Java method-tracing module (`qing.albatross.tracker`): `OceanTracker`, `OceanSinker`, event types, built-in keyword/string/default sinkers and `OceanLogger`.

###  `demo` 
The demo module is used to demonstrate the functionality of the Albatross Android framework. It contains the regression tests (`TestRunner`/`TestSuiteRunner`) and runnable examples for Java hooks, native hooks, CModule, Stalker and Ocean tracing.

### `app` 
Demo application (applicationId `qing.albatross.demo64`).

### `app32` 
32-bit demo application (applicationId `qing.albatross.demo32`).

## What's New in 4.0.0

- **Dynamic Java method hooks** — install before/after callbacks on any `Member` with `Albatross.hookMethod`; no mirror class required.
- **CModule / NativeFunction** — compile C in-process with tcc and call native functions by signature.
- **Stalker** — follow functions/symbols, consume CALL/RET/EXEC/BLOCK/COMPILE events, transform blocks and read/write registers.
- **Ocean tracer** — trace Java calls, returns and field access with `OceanTracker`/`OceanSinker`.
- **Async RPC** — mark slow RPC methods with `@AsyncRpc`.
- **Native-hook and stability updates** — delayed native hooks, `NativeCpuContext`, 16 KB page alignment, `HookRecord` layout sync and the x86 32-bit release `Thread::DecodeObject` fix.

## Usage Example
### 1. Hook activity method and access field.
```java
// Define Hooker class
@TargetClass(Activity.class)
public class ActivityH {

  //Active automatically,by analyze ActivityH dependencies. 
  @TargetClass(Bundle.class)
  public static class BundleH {
    @FieldRef
    public static Bundle EMPTY;

  }

  @FieldRef
  public boolean mCalled;

  @MethodHookBackup
  private void onCreate(BundleH savedInstanceState) {
    assert BundleH.EMPTY == Bundle.EMPTY;
    assert !mCalled;
    onCreate(savedInstanceState);
    assert mCalled;
  }
}



// Active hooker
public class AlbatrossDemoMainActivity extends Activity {
  @Override
  protected void onCreate(Bundle savedInstanceState) {
    try {
      Albatross.hookClass(ActivityH.class);
    } catch (AlbatrossException e) {
      throw new RuntimeException(e);
    }
    ActivityH self = Albatross.convert(this, ActivityH.class);
    assert !self.mCalled;
    super.onCreate(savedInstanceState);
    assert self.mCalled;
    fixLayout();
  }
}
```
### 2. Hook system server class `LocationManagerService`

```java

@TargetClass(className = {"com.android.server.LocationManagerService", "com.android.server.location.LocationManagerService"})
public class LocationManagerServiceH {


  @MethodHookBackup
  private  void requestLocationUpdates(LocationRequest request, @ParamInfo("android.location.ILocationListener") Object listener,
                                             PendingIntent intent, String packageName){
    requestLocationUpdates(request,listener,intent,packageName);
  }
} 

```

### 3. Transactional Hooking with Dependency Resolution

````java

@TargetClass
public static class ActivityClientRecord {
  @FieldRef
  public LoadedApk packageInfo;
  @FieldRef
  public Intent intent;
}

@TargetClass
public static class LoadedApk {
  @FieldRef
  public String mPackageName;
}


@TargetClass(className = "android.app.ActivityThread")
public static class ActivityThreadH {
  public static Class<?> Class;

  @StaticMethodBackup
  public static native Application currentApplication();

  //该方法仅仅是为了推导出ActivityClientRecord的正确类型，不会backup Method,所以标记DefOption.NOTHING
  @MethodBackup(option = DefOption.NOTHING)
  private native Activity performLaunchActivity(ActivityClientRecord r, Intent customIntent);

  //泛型的具体类型无法动态获取，所以需要通过上面的方法去推断出类型和依赖
  @FieldRef
  Map<IBinder, ActivityClientRecord> mActivities;

  @StaticMethodBackup
  public static native ActivityThreadH currentActivityThread();
}


public static void test() throws AlbatrossErr {
  assert Albatross.hookClass(ActivityThreadH.class) != 0;
  ActivityThreadH activityThread = ActivityThreadH.currentActivityThread();
  assert activityThread.getClass() == ActivityThreadH.Class;
  Application app = ActivityThreadH.currentApplication();
  String targetPackage = app.getPackageName();
  for (ActivityClientRecord record : activityThread.mActivities.values()) {
    assert targetPackage.equals(record.packageInfo.mPackageName);
  }
}

````
### 4. Binder hook
```java
@TargetClass
  static class ParceledListSlice<T> {
    @FieldRef(option = DefOption.VIRTUAL, required = true)
    public List<T> mList;
  }


  @TargetClass
  static class IPackageManager {
    public static int count = 0;

    @MethodHookBackup
    private ParceledListSlice<ResolveInfo> queryIntentActivities(Intent intent, String resolvedType, long flags, int userId) {

      ParceledListSlice<ResolveInfo> res = queryIntentActivities(intent, resolvedType, flags, userId);
      count = res.mList.size();
      return res;
    }

    @MethodHookBackup
    private ParceledListSlice<ResolveInfo> queryIntentActivities(Intent intent, String resolvedType, int flags, int userId) {
      ParceledListSlice<ResolveInfo> res = queryIntentActivities(intent, resolvedType, flags, userId);
      count = res.mList.size();
      return res;
    }
  }


  public static class PackageManagerH {
    @FieldRef(option = DefOption.INSTANCE)
    private IPackageManager mPM;
  }

  public static void test(boolean hook) throws AlbatrossErr {
    if (!Albatross.isFieldEnable())
      return;
    PackageManager packageManager = Albatross.currentApplication().getPackageManager();
    if (hook) {
      Albatross.hookObject(PackageManagerH.class, packageManager);
    } else
      IPackageManager.count = -1;
    Intent resolveIntent = new Intent(Intent.ACTION_MAIN, null);
    resolveIntent.addCategory(Intent.CATEGORY_LAUNCHER);
    List<ResolveInfo> res = packageManager.queryIntentActivities(resolveIntent, 0);
    assert res.size() == IPackageManager.count;
  }
```
### 5. Instruction hook

```java

 InstructionListener listener = null;

  public void instruction(View view) throws NoSuchMethodException {
    if (listener == null) {
      Method getCaller = AlbatrossDemoMainActivity.class.getDeclaredMethod("getCaller", View.class);
      listener = new InstructionListener() {
        @Override
        public void onEnter(Member method, Object self, int dexPc, InvocationContext invocationContext) {
          assert dexPc <= 10;
          assert dexPc >= 0;
          assert method == getCaller;
          assert self == AlbatrossDemoMainActivity.this;
          assert invocationContext.numberOfVRegs() == 7;
          Albatross.log("onEnter:" + dexPc);
          Object receiver = invocationContext.getParamObject(0, Object.class);
          assert receiver == self;
          Object v = invocationContext.getParamObject(1, Object.class);
          assert (v instanceof View);
          if (dexPc == 4) {
//          00003c44: 7100 b700 0000          0000: invoke-static       {}, Lqing/albatross/core/Albatross;->getCallerClass()Ljava/lang/Class; # method@00b7
//          00003c4a: 0c00                    0003: move-result-object  v0
            invocationContext.setVRegObject(0, AlbatrossDemoMainActivity.class);
          }
        }
      };
      assert Albatross.hookInstruction(getCaller, 0, 10, listener);
    } else {
      listener.unHook();
      listener = null;
    }
  }


```

### 6. Native Hook (3.6.0+, extended in 4.0.0)

Hook C/C++ functions in native libraries with the new `qing.albatross.nativehook` package:

```java
// Hook a native function by annotations
@TargetLibrary("log")
public class LiblogH {
  @Symbol("__android_log_print")
  static long logPrintAddr;

  @FuncBackup("__android_log_print")
  private static native int logPrintBackup(int prio, String tag, String msg);

  @FuncHook("__android_log_print")
  static int logPrint(int prio, String tag, String msg) {
    return logPrintBackup(prio, tag, "hooked: " + msg);
  }
}

AlbNative.hookNative(LiblogH.class, "log");

// Or open a library and look up symbols directly
DlInfo lib = AlbNative.openLib("libc.so");
if (lib != null) {
  long mallocAddr = lib.getSymbolAddress("malloc");
  lib.close();
}
```

### 7. Dynamic Java method hook

```java
Method target = Target.class.getDeclaredMethod("compute", int.class);
MethodCallHook hook = Albatross.hookMethod(target, new MethodHookCallback() {
  @Override
  public boolean beforeCall(CallFrame frame) {
    int v = frame.getParamInt(0);
    frame.setParamInt(0, v + 1);
    return true; // return false to skip the original method
  }

  @Override
  public void afterCall(CallFrame frame) {
    Object result = frame.getResult();
  }
});

// hook.addHook(anotherCallback);
// hook.unHook();
```

### 8. CModule & NativeFunction

```java
CModule cm = CModule.compile(
    "#include <albatross.h>\n"
    + "int add(int a, int b) { return a + b; }");
NativeFunction add = cm.getFunction("add", Const.ARG_INT, Const.ARG_INT);
int result = (Integer) add.call(2, 3); // 5
cm.close();
```

CModule supports `alb_module_init` / `alb_module_fini` lifecycle callbacks, compile caching and external symbol injection; `NativeFunction` can also call arbitrary native addresses.

### 9. Stalker code tracing

```java
StalkerConfig config = new StalkerConfig()
    .call(true).ret(true).block(true)
    .timeout(5000).maxEvents(200000);
config.eventListener = events -> {
  for (StalkerEvent e : events) {
    if (e.type == StalkerEvent.Type.CALL)
      Log.d("Stalker", "call " + e.location + " -> " + e.target);
  }
};
StalkerFollowHandle handle =
    Stalker.followSymbol("libtarget.so", "target_func", config);
targetFunction();
handle.unhook();
```

Transformers (`StalkerIterator`/`StalkerTransformer`) can drop instructions or insert `StalkerCallout` callbacks that read/write registers through `NativeCpuContext`. `followThread` is still experimental; prefer `followFunction`/`followSymbol`/`followCurrentThread`.

### 10. Ocean Java method tracing

```java
OceanTracker.init(0);
OceanTracker.setTraceTimeout(5000);
OceanTracker.setTraceMaxEvents(200000);
OceanTracker.traceMe(new OceanSinker() {
  @Override
  public void call(MethodCallContext context, Member member) {
    Log.d("Ocean", "call " + member);
  }
}, EventType.CALL);

// ... traced code ...

OceanTracker.stopTraceMe();
```

Ocean also provides `KeyWordSinker`/`StringSinker`/`DefaultSinker`, field read/write callbacks, package whitelists and `OceanLogger`.

### 11. Async RPC

```java
@AsyncRpc
public void dumpAll(String outputPath) {
  // Slow or potentially blocking RPC work; the agent runs it on a worker thread
  // instead of the single epoll loop.
}
```

## Use Cases
- **Hotfixes**: Replace buggy methods at runtime
- **Monitoring**: Intercept method calls for logging or analytics
- **Plugin Systems**: Dynamically load and modify behavior
- **Security**: Modify or block dangerous operations
- **SDK Interception**: Override or extend third-party SDK behavior
- **Binder Hook**:Easy for Multi-Instance Software Development.
- **Native Hook**:Hook system or third-party native libraries (e.g. libc) at the function level.
- **Reflection**:High-performance reflection library alternatives
- **Code analysis**:Tracking and analyzing the running logic through instructions

##  Future Plans
4.0.0 delivers Java instruction hooking, Java method tracing, dynamic Java hooks, CModule/Stalker and unhooking. Remaining work includes the arm32/arm64 cross-thread `followThread` stalker backends, broader vendor/API validation (notably 16 KB page devices and 32-bit ROMs), and more examples and documentation.
More tools and documentation will follow. Stay tuned for updates on `albatross-server`, `albatross-core`,`albatross-manager` and more!



## Acknowledgments
Inspired by the YAHFA framework while introducing architectural improvements for modern Android systems. Special thanks to the Xposed and SandHook projects for pioneering Android hooking techniques.


- [YAHFA](https://github.com/PAGalaxyLab/YAHFA)  Copyright (c) [PAGalaxyLab](https://github.com/PAGalaxyLab)
- [SandHook](https://github.com/asLody/SandHook)  Copyright (c) [asLody](https://github.com/asLody)


## License

Apache License 2.0
See [LICENSE](LICENSE) for details.


## Community

Join our QQ group for discussions: **478564202**

Topics we discuss include:
- Mobile security techniques
- Reverse engineering
- Risk control technologies
- Albatross framework usage
- Technical exchanges and sharing

##  Related Documentation

- [API Reference (full)](api_documentation.md) — Complete 4.0.0 API reference
- [Annotations](docs/annotatin_reference_EN.md) — Full list of hooking and configuration annotations
- [API Reference (legacy layout)](docs/api_documentation_EN.md) — Public methods and usage
- [Exception Handling](docs/exception_documentation_EN.md) — Error types and fix strategies
- [Changelog](Changelog) — Release history
