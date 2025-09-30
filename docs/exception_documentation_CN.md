# Albatross 异常文档

本文档描述了Albatross框架中更新的异常层次结构和错误处理逻辑，重点关注Hook操作期间的结构验证和必需元素检查。

正确的异常处理确保Hooker实现的健壮性和问题的快速解决。

## 异常层次结构

所有异常都继承自基础`AlbatrossException`类：

```
AlbatrossException
├── AlbatrossErr (关键Hooker错误的基础类)
│   ├── RequiredErr (必需元素未找到)
│   │   ├── RequiredFieldErr
│   │   ├── RequiredMethodErr
│   │   │── RequiredClassErr
│   │   └── RequiredInstanceErr
│   └── HookerStructErr (Hooker结构违规)
│       ├── RedundantFieldErr
│       ├── FindRedundantMethodErr
│       ├── VirtualCallBackupErr
│       └── NotNativeBackupErr
|      
├── MethodException (方法签名问题)
└── FieldException (字段验证错误)
```
---

## 核心异常

### 1. `AlbatrossErr`
**关键Hooker验证错误的基础类**  
扩展此类型的所有异常在抛出时都会**停止Hook过程**。

- **子类**：
  - `RequiredErr` - 必需元素缺失
  - `HookerStructErr` - 无效的Hooker结构

---

### 2. `RequiredErr`
**当必需元素无法解析时抛出**  
仅在注解中`required=true`时触发。

| 子类              | 触发条件         |
|-------------------|------------------|
| `RequiredFieldErr`    | 必需字段未找到  |
| `RequiredMethodErr`   | 必需方法未找到 |
| `RequiredClassErr`    | 目标类未找到    |
| `RequiredInstanceErr` | 未传递实例对象 |

**示例**：
```java
@TargetClass(name = "com.example.NonExistentClass", required = true)
public class BadHooker {} 
// → 抛出 RequiredClassErr
```

---

### 3. `HookerStructErr`
**结构违规的基础类**  
表示无效的Hooker设计模式。

#### 子类：
- `RedundantFieldErr` - 未使用的实例字段
- `FindRedundantMethodErr` - 未使用的实例方法
- `VirtualCallBackupErr` - 备份方法不是私有的
- `NotNativeBackupErr` - 备份方法不是native的

**验证规则**：
- Hooker只能包含带注解的实例字段/方法
- 备份实例方法必须是private和native的。

#### `RedundantFieldErr`
**当Hooker类包含未注解的实例字段时抛出**

- **构造函数**：
  ```java
  public RedundantFieldErr(Field f)
  ```
    - `f`: 冗余字段引用

- **错误示例**：
  ```java
  @TargetClass(String.class)
  public class StringHooker {
      // 未注解字段 → RedundantFieldErr
      public int unusedField;
  }
  ```

- **修复**：
    - 删除未使用的字段
    - 如果不需要实例关联，标记为`static`

---

#### `FindRedundantMethodErr`
**当Hooker类包含未注解的实例方法时抛出**

- **构造函数**：
  ```java
  public FindRedundantMethodErr(Method method)
  ```
    - `method`: 冗余方法引用

- **错误示例**：
  ```java
  @TargetClass(Activity.class)
  public class ActivityHooker {
      // 实例方法 → FindRedundantMethodErr
      public void unusedMethod() {}
  }
  ```

- **修复**：
    - 删除未使用的实例方法
    - 添加注解或标记为`static`

---

#### `VirtualCallBackupErr`
**当备份实例方法未声明为private时抛出**

- **构造函数**：
  ```java
  public VirtualCallBackupErr(Method method)
  ```
    - `method`: 无效的备份方法引用

- **错误示例**：
  ```java
  @MethodHookBackup
  public  void onCreate$Hook(Bundle savedInstanceState) {
    //....   hook logic 
    onCreate$Hook(savedInstanceState);//VirtualCallBackupErr
  }
  ```

- **修复**：
    - 将备份方法标记为`private`
    - 将方法改为静态方法
  ```java
    @MethodHookBackup
    private static void onCreate(Activity activity, Bundle savedInstanceState) {
        // hook implementation
      onCreate(activity,savedInstanceState)
    }
  ```

---

#### `NotNativeBackupErr`
**当备份实例方法未声明为native时抛出**

- **构造函数**：
  ```java
  public NotNativeBackupErr(Method method)
  ```
  - `method`: 无效的备份方法引用

- **错误示例**：
  ```java
  @MethodBackup
  private  void onCreate$Backup(Bundle savedInstanceState) {
    //....    
  }
  ```

- **修复**：
  - 将备份方法标记为`native`
  ```java
    @MethodBackup
  private  native void onCreate$Backup(Bundle savedInstanceState);
  ```

---

### 4. `MethodException`
**表示方法签名不匹配**

#### 错误代码（`MethodException`）
| 代码                     | 描述                        |
|--------------------------|----------------------------|
| `WRONG_ARGUMENT`         | 参数类型不匹配            |
| `WRONG_RETURN`           | 返回类型不匹配               |
| `ARGUMENT_SIZE_NOT_MATCH` | 参数数量不匹配          |

- **场景**：
    - 参数类型/数量不匹配
    - 无效的返回类型声明
    - 缺少`static`修饰符

- **解决方案**：
    - 使用`@ParamInfo`进行显式签名定义
    - 完全匹配目标方法的签名

---

### 5. `FieldException`
**字段验证错误**

| 原因               | 描述                              |
|-------------------|-----------------------------------|
| `WRONG_STATIC_FIELD` | 字段间静态状态不匹配  |
| `WRONG_TYPE`         | 字段间类型不匹配             |

---

## API异常契约

### `hookClass(Class<?> hooker)`
```java
public static int hookClass(Class<?> hooker) throws AlbatrossErr
```
- **关键错误**：
  - `RequiredErr` - 必需元素缺失
  - `HookerStructErr` - 结构违规
- **非关键错误**：
  - `FieldException`/`MethodException` - 将被捕获并记录，Hook继续

### `backupAndHook(Member target, Method hook, Method backup)`
```java
public static boolean backupAndHook(Member target, Method hook, Method backup) throws AlbatrossException
```
- **可能的异常**：
  - `MethodException` - 方法签名不匹配
  - `VirtualCallBackupErr` - 备份方法可见性错误

---

## API错误映射

| API方法             | 可能的异常                        |
|---------------------|----------------------------------|
| `hookClass`           | AlbatrossErr                    |
| `backupAndHook`       | AlbatrossException                |
| `backupField`         | FieldException, AlbatrossErr               |
| `backup`              | AlbatrossException                |
| `hookObject`              | AlbatrossErr                |

## 异常处理模式

### 1. 必需元素处理
```java
try {
    Albatross.hookClass(Hooker.class);
} catch (RequiredErr e) {
    // 由于必需元素缺失导致的关键失败
    Log.e("Albatross", "Required element missing: " + e.getMessage());
}
```

### 2. 结构验证
```java
try {
    Albatross.hookClass(Hooker.class);
} catch (HookerStructErr e) {
    if (e instanceof RedundantFieldErr) {
        Log.w("Albatross", "Redundant field: " + ((RedundantFieldErr)e).field.getName());
    }
}
```

---

## 有效Hooker示例

```java
@TargetClass(String.class)
public class StringHooker {
    @FieldRef(required = true)
    static int count; // 必需字段必须存在

    @MethodHook(name = "substring", required = false)
    private static String substringHook(String str, int start, int end) {
        // Hook实现
       
    }
    
}
```

## 常见错误模式

| 错误类型           | 原因                                   | 修复                                                                     |
|-------------------|----------------------------------------|-------------------------------------------------------------------------|
| RedundantFieldErr    | 未注解的实例字段              | 删除或标记为static                                                |
| Redundant Method     | 未注解的实例方法             | 删除或添加注解                                                |
| VirtualCallBackupErr | 非私有备份方法               | 添加private修饰符                                                    |
| NotNativeBackupErr   | 非native备份方法                | 添加native修饰符                                                     |
| Parameter Mismatch   | 与目标方法签名不匹配   | 匹配目标方法签名或使用@ParamInfo或@SubType注解 |
| Return Type Mismatch | 返回类型与目标方法不匹配 | 调整返回类型声明                                          |
| RequiredFieldErr     | 必需字段未找到                | 删除缺失字段或设置required=false                              |
| RequiredMethodErr    | 必需方法未找到               | 删除缺失方法或设置required=false                             |
| RequiredClassErr     | 必需类未找到                | 删除缺失类或设置required=false                              |
| RequiredInstanceErr  | 必需实例为null               | 调用`hookObject`时传递目标类的实例对象      |

---

## 关键设计原则

1. **必需元素**：
  - 仅在`required=true`时失败
  - 非必需元素失败会被记录,不会退出

2. **Hooker结构**：
  - 必须完全镜像目标类结构
  - 不允许额外的实例字段/方法
  - 备份方法必须是非虚拟和native的

3. **错误恢复**：
  - 结构错误是致命的
  - 签名不匹配是可接受的
  - 字段/方法解析错误根据`required`标志条件是否抛出异常

---

## 调试技巧

### 1. 启用详细日志
```java
Albatross.loadLibrary("albatross_base", Albatross.FLAG_DEBUG);
```

### 2. 检查Hook状态
```java
if (Albatross.isHooked(TargetClass.class)) {
    Log.d("Albatross", "Class is hooked successfully");
}
```

### 3. 验证字段支持
```java
if (Albatross.isFieldEnable()) {
    Log.d("Albatross", "Field hooking is enabled");
} else {
    Log.w("Albatross", "Field hooking is disabled");
}
```

### 4. 异常处理最佳实践
```java
try {
    Albatross.hookClass(MyHooker.class);
} catch (RequiredErr e) {
    // 处理必需元素缺失
    Log.e("Albatross", "Required element missing", e);
} catch (HookerStructErr e) {
    // 处理结构错误
    Log.e("Albatross", "Hooker structure error", e);
} catch (AlbatrossException e) {
    // 处理其他异常
    Log.e("Albatross", "Hook failed", e);
}
```

---

本异常文档提供了Albatross框架中错误处理的完整指南。通过理解这些异常类型和处理模式，开发者可以构建更健壮的Hook实现并快速解决遇到的问题。
