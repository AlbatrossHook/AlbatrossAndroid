# Albatross 注解参考

Albatross是一个基于注解驱动的Android Hook和反射框架，旨在运行时动态修改类和方法的行为。它通过Java注解提供声明式Hook方法，允许开发者定义**Hooker**——表示被Hook目标类的镜像类。Albatross支持多种执行策略，包括**JIT（即时编译）**、**AOT（预编译）**和**解释模式**，可通过配置选项进行控制。

---

## 核心概念

### 1. Hooker
**Hooker**是一个镜像目标类并包含Hook或备份其方法和字段逻辑的类。它作为代理来拦截和修改原始类的行为。

#### Hooker的职责：
- 通过`@MethodHook`、`@StaticMethodHook`或`@ConstructorHook`定义目标方法的替换逻辑
- 通过`@MethodBackup`、`@StaticMethodBackup`或`@ConstructorBackup`定义原始行为的备份逻辑
- 使用`@FieldRef`访问和操作目标类的字段
- **执行配置**控制代码如何编译和执行（JIT/AOT/解释模式）

### 2. TargetClass
**目标类**是被Hook的类。它通过在Hooker类上使用`@TargetClass`注解定义，或手动传递给`hookClass(hooker, targetClass)`方法。

#### `@TargetClass`属性：
| 属性        | 类型            | 描述 |
|-------------|-----------------|------|
| `value()`        | `Class<?>`      | 目标类类型 |
| `className()`    | `String[]`      | 要匹配的类名数组（用于不可访问或延迟加载的类） |
| `pendingHook()`  | `boolean`       | 如果为true，Hook将延迟到类加载时执行 |
| `hookerExec()`| `int`           | Hooker本身的执行策略 |
| `targetExec()`| `int`           | 目标类的执行策略 |
| `required()`     | `boolean`       | 如果为true，找不到类时抛出错误 |

---

## Hook和备份注解

Albatross提供了丰富的注解来定义方法和构造函数的Hook和备份逻辑。

### 方法Hook
| 注解              | 描述 |
|-------------------|------|
| `@MethodHook`           | 替换目标方法 |
| `@MethodBackup`         | 备份原始方法 |
| `@MethodHookBackup`     | `@MethodHook`和`@MethodBackup`的组合 |
| `@StaticMethodHook`     | 替换静态方法 |
| `@StaticMethodBackup`   | 备份静态方法 |
| `@StaticMethodHookBackup` | `@StaticMethodHook`和`@StaticMethodBackup`的组合 |
| `@ConstructorHook`      | 替换构造函数 |
| `@ConstructorBackup`    | 备份构造函数 |
| `@ConstructorHookBackup`| `@ConstructorHook`和`@ConstructorBackup`的组合 |

### 通用注解属性：
| 属性        | 类型            | 描述                                            |
|-------------|-----------------|------------------------------------------------|
| `name()`         | `String[]`      | 要Hook的方法名称                           |
| `value()`        | `String[]`      | 参数类名                                   |
| `isStatic()`     | `boolean`       | 指示方法是否为静态                      |
| `targetClass()`  | `Class<?>`      | 显式指定目标类                    |
| `className()`    | `String[]`      | 延迟或不可访问类的类名       |
| `required()`     | `boolean`       | 如果为true，找不到方法时抛出错误 |
| `hookerExec()`| `int`           | Hook方法的执行策略               |
| `targetExec()`| `int`           | 原始方法的执行策略           |
| `option()`       | `int`           | Hook行为选项（如`DefOption.VIRTUAL`）    |
| `minSdk()`, `maxSdk()` | `int`     | Hook的SDK版本约束                    |

---

## 字段访问

使用`@FieldRef`从Hooker访问目标类的字段。

### `@FieldRef`属性：
| 属性        | 类型            | 描述                                           |
|-------------|-----------------|------------------------------------------------|
| `value()`        | `String[]`      | 要访问的字段名称                         |
| `targetClass()`  | `Class<?>`      | 显式指定目标类                   |
| `className()`    | `String[]`      | 延迟或不可访问类的类名      |
| `required()`     | `boolean`       | 如果为true，找不到字段时抛出错误 |
| `option()`       | `int`           | 解析选项（如`DefOption.VIRTUAL`）  |

---

## 执行选项（`ExecOption`）
Albatross允许使用位标志对Hooker和目标代码在运行时的执行方式进行细粒度控制。

### 支持的编译选项
| 标志                     | 描述                    |
|--------------------------|------------------------|
| `DO_NOTHING`           | 不编译                 |
| `DEFAULT_OPTION`        | 使用默认执行策略 |
| `JIT_OSR`            | 栈上替换（OSR）     |
| `JIT_BASELINE`       | 基线编译           |
| `JIT_OPTIMIZED`      | 优化编译          |
| `INTERPRETER`      | 使用解释器模式               |
| `COMPILE_DISABLE_AOT`    | 禁用AOT代码               |
| `COMPILE_DISABLE_JIT`    | 禁用JIT编译        |
| `COMPILE_AOT`            | 使用AOT                        |

### 常见组合：
| 组合               | 描述               |
|-------------------|-------------------|
| `RECOMPILE_OSR`         | JIT重新编译 + OSR       |
| `RECOMPILE_BASELINE`    | JIT重新编译 + 基线  |
| `RECOMPILE_OPTIMIZED`   | JIT重新编译 + 优化 |

---

## 定义选项（`DefOption`）

控制Albatross如何在目标类中解析方法和字段。

| 选项        | 描述 |
|-------------|------|
| `DEFAULT`     | 使用默认解析策略 |
| `NOTHING`     | 不Hook或备份 |
| `VIRTUAL`     | 如需要从父类解析 |
| `INSTANCE`    | 使用实例确定字段类型 |

---

## 参数匹配

Albatross通过注解支持高级参数匹配：

### `@ParamInfo`
用于在类型推断不可能时显式指定参数的类名。

```java
void someMethod(@ParamInfo("com.example.MyClass") Object obj);
```

### `@SubType`
指示参数可以是声明类型的子类。

```java
void someMethod(@SubType MyClass obj);
```

---

## 错误处理和必需标志

- **AlbatrossErr**: 表示Hooker类中的结构或关键错误。
- **RequiredErr**: 如果找不到必需字段/方法/类且`required=true`时抛出。

### 错误处理策略：
- 如果`required = true`且元素缺失 → 抛出异常并中止Hook
- 如果`required = false`且元素缺失 → 记录警告并继续

---

## 快速开始

### 步骤1：定义Hooker类

```java
@TargetClass(Activity.class)
public class ActivityH {

  // 由ActivityH自动激活
  @TargetClass(Bundle.class)
  public static class BundleH {
    @FieldRef
    public static Bundle EMPTY;

  }

  @FieldRef
  public boolean mCalled;


  @MethodBackup(option = DefOption.VIRTUAL)
  private native boolean isFinishing();

  public static boolean finish(ActivityH h) {
    return h.isFinishing();
  }

  @MethodHookBackup
  private void onCreate(BundleH savedInstanceState) {
    assert BundleH.EMPTY == Bundle.EMPTY;
    assert !mCalled;
    onCreate(savedInstanceState);
    assert mCalled;
  }
}

```

### 步骤2：应用Hook逻辑

```java
try {
    Albatross.hookClass(ActivityH.class);
} catch (AlbatrossErr e) {
    Log.e("Albatross", "Hook failed: " + e.getMessage());
}
```

---

## 高级用法

### 延迟Hook
```java
@TargetClass(className = "com.example.LazyClass", pendingHook = true)
public class LazyClassHooker {
    @MethodHook
    private void someMethod() {
        // Hook逻辑
    }
}
```

### 字段访问示例
```java
@TargetClass(className = "com.example.TargetClass")
public class TargetHooker {
    @FieldRef(required = true)
    public String mSecretField;
    
    @FieldRef(option = DefOption.VIRTUAL)
    public static int sStaticField;
    
    public static void accessFields(Object target) {
        TargetHooker hooker = Albatross.convert(target, TargetHooker.class);
        String secret = hooker.mSecretField;
        int staticValue = TargetHooker.sStaticField;
    }
}
```

### 构造函数Hook
```java
@TargetClass(className = "com.example.TargetClass")
public class ConstructorHooker {
    @ConstructorHook
    private void init(String param) {
        Log.d("Albatross", "Constructor called with: " + param);
        init(param); // 调用原始构造函数
    }
}
```

### 静态方法Hook
```java
@TargetClass(className = "com.example.Utils")
public class UtilsHooker {
    @StaticMethodHook
    private static String processData(String input) {
        Log.d("Albatross", "Processing: " + input);
        return processData(input); // 调用原始方法
    }
}
```

---

## 最佳实践

1. **使用事务进行批量Hook**：
   ```java
   Albatross.transactionBegin();
   Albatross.hookClass(Hooker1.class);
   Albatross.hookClass(Hooker2.class);
   Albatross.transactionEnd(true);
   ```

2. **合理设置执行选项**：
   ```java
   @TargetClass(targetExec = ExecOption.JIT_OPTIMIZED, 
                hookerExec = ExecOption.INTERPRETER)
   public class OptimizedHooker {
       // Hooker实现
   }
   ```

3. **使用required标志控制错误处理**：
   ```java
   @FieldRef(required = false)  // 可选字段
   public String optionalField;
   
   @MethodHook(required = true)  // 必需方法
   private void criticalMethod() {}
   ```

4. **利用参数类型推断**：
   ```java
   @MethodHook
   private void methodWithComplexParams(@ParamInfo("com.example.ComplexType") Object param) {
       // 复杂参数类型的Hook
   }
   ```

---

本注解参考文档提供了使用Albatross注解系统构建强大Hook场景的完整指南。结合[API文档](api_documentation_CN.md)使用这些注解可以实现复杂的运行时行为修改。
