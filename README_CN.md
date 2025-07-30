# Albatross Android - 为安卓设计的Hook与反射框架

[English](README.md)
----------------

## 概述
**Albatross Android** 是一款专为安卓系统（Android 7.0 至 Android 16 Baklava）设计的高性能、低影响的Hook与反射框架。

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
- **功能强大**：既可用作Hook库，也可以作为反射替代方案（零反射开销)。
- **易于开发**：构建镜像类和自动推导简化了复杂的Hook逻辑

### 平台支持
- **安卓版本**：
  - 全面支持：API 26-36（8.0-16）
  - 指令Hook：API 24-36（7.0-16）
  - 字段访问：API 24-36（8.0-16），8.0以下因Dex优化限制，字段访问被禁用。
  - 方法Hook：API 24-36（7.0-16）
  - ❌ 不支持：API 23 及以下（6.0 Marshmallow 及更早版本）
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
该模块负责rpc调用，用于和沙箱通信。

### `demo`
演示模块用于展示Albatross Android框架的功能。它包含用于测试Hook功能。通过连续点击“加载”按钮进行测试。

### `app`
用于Albatross测试的安卓应用。

### `app32`
与`app`模块类似，用于测试32位的架构（arm,x86）。

## 使用示例
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

## 应用场景
- **热修复**：在运行时替换有缺陷的方法
- **监控**：拦截方法调用以进行日志记录或分析
- **插件系统**：动态加载和修改行为
- **BinderHook**：非常适合多开软件的开发。
- **反射**：高性能反射库替代方案
- **代码分析**：通过指令跟踪和分析运行逻辑

## 未来计划
潜在功能包括~~Java指令Hook~~、Java代码追踪、动态Hook（单个方法可Hook多个类的方法）、调用链Hook和取消Hook能力。然而，由于精力限制，这些功能的实现将根据用户反馈进行优先级排序。
更多工具和文档将随后推出。敬请关注`albatross-server`、`albatross-core`、`albatross-manager`等项目的更新！

## 致谢
本框架受YAHFA框架启发，特别感谢Xposed和SandHook项目在安卓Hook技术方面的开创性贡献。

## 版权声明

- [YAHFA](https://github.com/PAGalaxyLab/YAHFA)  版权所有 (c) [PAGalaxyLab](https://github.com/PAGalaxyLab)
- [SandHook](https://github.com/asLody/SandHook)  版权所有 (c) [asLody](https://github.com/asLody)

## 许可证

Apache许可证2.0
详见[LICENSE](LICENSE)。

## 相关文档

- [注解](docs/annotatin_reference.md) — 注解使用文档
- [API参考](#) — 核心api用法
- [异常处理](docs/exception_documentation.md) — 异常说明以及修复文档