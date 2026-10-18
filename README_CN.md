# Albatross Android - 为安卓设计的Hook与反射框架

[English](README.md)
----------------

## 概述
**Albatross Android** 是一款专为安卓系统（Android 7.0+，API 24+；4.0.0 已在 Android 17 / API 37 及 16KB 页设备上验证）设计的高性能、低影响的Hook与反射框架。

该框架通过**Hooker类（镜像类**实现方法Hook/字段访问，这些类以声明方式描述目标。系统自动推断目标方法/字段，并允许与Hook的类进行无缝交互。与传统基于反射的方法不同，它在保持安全性和兼容性的同时，消除了反射和构建参数，校验等额外开销。

---

## 设计原则

本框架属于YAHFA派系，在提供强大灵活的Hook能力的同时，保持了系统稳定性，极致的运行的性能。

Albatross 遵循以下设计目标：

1. **高性能**：注重执行速度和内存占用，保持Profile文件生成和方法内联（被Hook的方法除外）。
2. **代码优雅**：简化hook逻辑，易于开发，可读性高，不繁琐。
3. **最小系统影响**：不主动触发类初始化，保持非公开API的限制，hook不挂起vm。

## 主要特性

### 核心功能
- **镜像类Hook**：构建Hooker（镜像类）来拦截方法和访问字段
- **透明转换**：将目标类无缝转换为Hooker
- **批量操作**：基于事务的Hook，自动解决依赖关系
- **零反射设计**：直接调用方法和访问字段，无反射开销,调用没有额外开销。
- **延迟Hook**：不用考虑类加载时机,对于加壳的app非常有用。
- **Native Hook**：Hook native（C/C++）库中的函数（`qing.albatross.nativehook`，3.6.0新增；4.0.0扩展延迟Hook、CModule/NativeFunction 与 Stalker）。
- **动态 Java Hook**：`Albatross.hookMethod(Member, MethodHookCallback)` 可在任意方法上安装 before/after 回调，无需预先生成镜像类。
- **进程内 CModule**：`CModule` 用 tcc 在运行时编译 C 代码；`NativeFunction` 按签名调用编译函数或任意 native 地址。
- **代码追踪**：`Stalker` 提供指令级追踪，支持 CALL/RET/EXEC/BLOCK/COMPILE 事件、transformer 与寄存器级 callout。
- **Java 方法追踪**：`ocean` 模块（`OceanTracker`/`OceanSinker`）追踪方法调用、返回与字段读写，内置 keyword/string/default sinker。
- **异步 RPC**：`@AsyncRpc` 把耗时 RPC 方法放到工作线程，不占用 agent 的 epoll 循环。
- **16KB 页支持**：native 库按 Android 15+ 16KB 页设备对齐。
- **功能强大**：既可用作Hook库，也可以作为反射替代方案（零反射开销)。
- **易于开发**：构建镜像类和自动推导简化了复杂的Hook逻辑

### 平台支持
- **安卓版本**：
  - 4.0.0 已验证：API 29、30、34、35、37（x86、x86_64、arm64，含 16KB 页设备）；API 24-28 使用同一兼容路径
  - 全面支持：API 26-37（8.0-17）
  - 指令Hook：API 24-37（7.0-17）
  - 字段访问：API 26-37（8.0-17），8.0以下因Dex优化限制，字段访问被禁用。
  - 方法Hook：API 24-37（7.0-17）
  - ❌ 不支持：API 23 及以下（6.0 Marshmallow 及更早版本）
  - 集成: 需要保证目标应用进程没有Lsposed环境,使用frida注入需要清除掉frida的java环境
- **架构**：x86、x86_64、ARM、ARM64

### 安全与稳定性
- 不触发类初始化
- 保持非公开API限制，同时实现隐藏方法/字段的访问
- 保持PGO生成和内联编译（目标方法除外）
- 调试/发布：
  - 调试：稳定执行,jdwp调试不出错
  - 发布：Hooker和目标类以机器码方式运行

---

## Albatross和其它框架对比

| 特性 | 传统框架 | Albatross |
|---------------------|-----------------------------------------|-------------------------------------------|
| 类初始化 | 触发类初始化 | 不触发 |
| 性能 | 反射开销大,需构建参数，调用链路长 | 原生机器码速度 |
| 系统影响 | 禁用Profield/内联 | 保留编译器优化 |
| 安全性 | 绕过API限制 | 保持非公开API策略 |
| 批量Hook | 无法原子化Hook类及其依赖项 | 基于事务hook，失败可撤销 |
| pendingHook | 不支持（lazy初始化并不是） | 在目标类初始化时hook |
| 指令Hook | 不支持 | 支持 |
---

## 项目结构

### `annotation`
该模块包含整个项目中使用的注解。注解可用于提供关于类、方法或字段的元数据，这些元数据可在运行时进行处理。

### `core`
核心模块提供了Albatross Android框架的基本功能。它包含`Albatross`类，该类提供了各种与Hook相关的方法，如方法Hook、备份和字段备份。

### `server`
RPC 服务端/客户端实现，包括用于耗时方法的 `@AsyncRpc` 异步派发。

### `ocean`
Java 方法追踪模块（`qing.albatross.tracker`）：`OceanTracker`、`OceanSinker`、事件类型、内置 keyword/string/default sinker 与 `OceanLogger`。

### `demo`
演示与回归测试模块，包含 `TestRunner`/`TestSuiteRunner`，覆盖 Java Hook、Native Hook、CModule、Stalker 与 Ocean trace。

### `app`
演示应用（applicationId `qing.albatross.demo64`）。

### `app32`
32 位演示应用（applicationId `qing.albatross.demo32`）。

## 4.0.0 新增内容

- **动态 Java 方法 Hook**：`Albatross.hookMethod` 可在任意 `Member` 上安装 before/after 回调，无需镜像类。
- **CModule / NativeFunction**：进程内 tcc 编译 C 代码，并按签名调用 native 函数。
- **Stalker**：支持按函数/符号追踪、CALL/RET/EXEC/BLOCK/COMPILE 事件、块变换与寄存器读写。
- **Ocean tracer**：`OceanTracker`/`OceanSinker` 追踪 Java 调用、返回与字段读写。
- **异步 RPC**：`@AsyncRpc` 标记耗时 RPC 方法。
- **Native Hook 与稳定性**：延迟 native Hook、`NativeCpuContext`、16KB 页对齐、`HookRecord` 布局同步，以及 x86 32 位 release 的 `Thread::DecodeObject` 修复。

## 使用示例
### 初始化
1.不混淆代码的情况下,不可以配置初始化参数,不推荐
```java
  System.loadLibrary("albatross_base");
```
2.jni注册，调用java api初始化
```cpp

JNICALL jboolean registerAlbatrossLib(JNIEnv *env, jclass clazz,jclass albatross) {
    int err_code = AlbatrossAndroidInit(env, albatross);
    if (err_code)
        return JNI_FALSE;
    return JNI_TRUE;
}
```

```java
 registerAlbatrossLib(Albatross.class);
 Albatross.init(0);
```

### 1. HookActivity方法并访问字段。
```java
// 定义Hooker类
@TargetClass(Activity.class)
public class ActivityH {

  //通过分析ActivityH依赖项自动激活。 
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



// 激活Hooker
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
### 2. Hook系统服务类`LocationManagerService`

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

### 3. 依赖解析的事务Hook

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
### 4. BinderHook
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
### 5. 指令Hook

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

### 6. Native Hook（3.6.0+，4.0.0 扩展）

使用新增的 `qing.albatross.nativehook` 包Hook native（C/C++）库中的函数：

```java
// 通过注解Hook native函数
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

// 或者直接打开动态库查找符号
DlInfo lib = AlbNative.openLib("libc.so");
if (lib != null) {
  long mallocAddr = lib.getSymbolAddress("malloc");
  lib.close();
}
```

### 7. 动态 Java 方法 Hook

```java
Method target = Target.class.getDeclaredMethod("compute", int.class);
MethodCallHook hook = Albatross.hookMethod(target, new MethodHookCallback() {
  @Override
  public boolean beforeCall(CallFrame frame) {
    int v = frame.getParamInt(0);
    frame.setParamInt(0, v + 1);
    return true; // 返回 false 可跳过原方法
  }

  @Override
  public void afterCall(CallFrame frame) {
    Object result = frame.getResult();
  }
});

// hook.addHook(anotherCallback);
// hook.unHook();
```

### 8. CModule 与 NativeFunction

```java
CModule cm = CModule.compile(
    "#include <albatross.h>\n"
    + "int add(int a, int b) { return a + b; }");
NativeFunction add = cm.getFunction("add", Const.ARG_INT, Const.ARG_INT);
int result = (Integer) add.call(2, 3); // 5
cm.close();
```

CModule 支持 `alb_module_init` / `alb_module_fini` 生命周期回调、编译缓存与外部符号注入；`NativeFunction` 也可直接调用任意 native 地址。

### 9. Stalker 代码追踪

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

`StalkerIterator`/`StalkerTransformer` 可丢弃指令或插入 `StalkerCallout`，通过 `NativeCpuContext` 读写寄存器。`followThread` 仍为实验性，优先使用 `followFunction`/`followSymbol`/`followCurrentThread`。

### 10. Ocean Java 方法追踪

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

// ... 被追踪的代码 ...

OceanTracker.stopTraceMe();
```

Ocean 还提供 `KeyWordSinker`/`StringSinker`/`DefaultSinker`、字段读写回调、包白名单与 `OceanLogger`。

### 11. 异步 RPC

```java
@AsyncRpc
public void dumpAll(String outputPath) {
  // 耗时或可能阻塞的 RPC：agent 会放到工作线程执行，不占用 epoll 循环
}
```

## 应用场景
- **热修复**：在运行时替换有缺陷的方法
- **监控**：拦截方法调用以进行日志记录或分析
- **插件系统**：动态加载和修改行为
- **BinderHook**：非常适合多开软件的开发。
- **Native Hook**：函数级Hook系统或第三方native库（如libc）。
- **反射**：高性能反射库替代方案
- **代码分析**：通过指令跟踪和分析运行逻辑

## 未来计划
4.0.0 已实现 Java 指令 Hook、Java 方法追踪、动态 Java Hook、CModule/Stalker 与取消 Hook。剩余工作包括 arm32/arm64 跨线程 `followThread` 后端、更多厂商/API 验证（尤其 16KB 页与 32 位 ROM）以及更多示例和文档。
更多工具和文档将随后推出。敬请关注`albatross-server`、`albatross-core`、`albatross-manager`等项目的更新！

## 致谢
本框架受YAHFA框架启发，特别感谢Xposed和SandHook项目在安卓Hook技术方面的开创性贡献。

- [YAHFA](https://github.com/PAGalaxyLab/YAHFA)  版权所有 (c) [PAGalaxyLab](https://github.com/PAGalaxyLab)
- [SandHook](https://github.com/asLody/SandHook)  版权所有 (c) [asLody](https://github.com/asLody)

## 许可证

Apache许可证2.0
详见[LICENSE](LICENSE)。

## 交流群

欢迎加入我们的QQ交流群：**478564202**

在这里可以讨论：
- 移动安全技术
- 逆向工程
- 风控技术
- Albatross框架使用问题
- 技术交流与分享

## 相关文档

- [完整 API 参考](api_documentation.md) — 4.0.0 完整 API
- [注解参考](docs/annotatin_reference_CN.md) — 注解使用文档
- [API参考（旧版布局）](docs/api_documentation_CN.md) — 核心API用法
- [异常处理](docs/exception_documentation_CN.md) — 异常说明以及修复文档
- [Changelog](Changelog) — 版本更新记录