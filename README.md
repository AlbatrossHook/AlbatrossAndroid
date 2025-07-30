# Albatross Android - Hook And Reflection Framework for Android

[中文版本](README_CN.md)
----------------

## Overview
**Albatross Android** is a high-performance, low-impact hooking and reflection framework designed for Android systems (Android 7.0 - Android 16 Baklava Preview). It is part of the broader **Albatross** ecosystem (including Albatross Server, Core, Manager, etc.), originally named after a nostalgic VR project from the developer's university days.

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
- **Multi-Use**: Functions as both hooking library and high-performance reflection alternative.
- **Easy Deployment**: Mirror-class  simplifies complex hooking logic

###  Platform Support
- **Android Versions**:
    - Full support: API 26-36 (8.0-16)
    - Instruction hook: API 24-36 (7.0-16)
    - Method hook: API 24-36 (7.0-16)
    - File access: API 26-36 (8.0-16)
    - ❌ Unsupported: API 23 and below (6.0 Marshmallow and earlier)
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
This module is responsible for rpc call.

###  `demo` 
The demo module is used to demonstrate the functionality of the Albatross Android framework. It contains Java source files for testing hooking functions.Please note, test by continuously clicking the "load" button.

### `app` 
Android application for Albatross test.

### `app32` 
 Similar to the `app` module, but specifically configured for 32 - bit Android applications.

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

  //该方法仅仅是为了推导出ActivityClientRecord的正确类型，不会backup Method,所以标记MethodDefOption.NOTHING
  @MethodBackup(option = MethodDefOption.NOTHING)
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
      listener = Albatross.hookInstruction(getCaller, 0, 10, (method, self, dexPc, invocationContext) -> {
        assert dexPc <= 10;
        assert dexPc >= 0;
        assert method == getCaller;
        assert self == AlbatrossDemoMainActivity.this;
        assert invocationContext.NumberOfVRegs() == 7;
        Albatross.log("onEnter:" + dexPc);
        Object receiver = invocationContext.GetParamReference(0);
        assert receiver == self;
        Object v = invocationContext.GetParamReference(1);
        assert (v instanceof View);
        if (dexPc == 4) {
//          00003c44: 7100 b700 0000          0000: invoke-static       {}, Lqing/albatross/core/Albatross;->getCallerClass()Ljava/lang/Class; # method@00b7
//          00003c4a: 0c00                    0003: move-result-object  v0
          invocationContext.SetVRegReference(0, AlbatrossDemoMainActivity.class);
        }
      });
    } else {
      listener.unHook();
      listener = null;
    }
  }


```

## Use Cases
- **Hotfixes**: Replace buggy methods at runtime
- **Monitoring**: Intercept method calls for logging or analytics
- **Plugin Systems**: Dynamically load and modify behavior
- **Security**: Modify or block dangerous operations
- **SDK Interception**: Override or extend third-party SDK behavior
- **Binder Hook**:Easy for Multi-Instance Software Development.
- **Reflection**:High-performance reflection library alternatives
- **Code analysis**:Tracking and analyzing the running logic through instructions

##  Future Plans
Potential features include ~~Java instruction hooking~~, Java code tracing, dynamic hooking (where a single method can hook methods from multiple classes), call chain hooking, and unhooking capabilities. However, due to resource limitations, the implementation of these features will be prioritized based on user feedback.
More tools and documentation will follow. Stay tuned for updates on `albatross-server`, `albatross-core`,`albatross-manager` and more!



## Acknowledgments
Inspired by the YAHFA framework while introducing architectural improvements for modern Android systems. Special thanks to the Xposed and SandHook projects for pioneering Android hooking techniques.


## Credits

- [YAHFA](https://github.com/PAGalaxyLab/YAHFA)  Copyright (c) [PAGalaxyLab](https://github.com/PAGalaxyLab)
- [SandHook](https://github.com/asLody/SandHook)  Copyright (c) [asLody](https://github.com/asLody)


## License

Apache License 2.0
See [LICENSE](LICENSE) for details.


##  Related Documentation

- [Annotations](docs/annotatin_reference.md) — Full list of hooking and configuration annotations
- [API Reference](#) — Public methods and usage
- [Exception Handling](docs/exception_documentation.md) — Error types and fix strategies
