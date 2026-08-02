# Albatross API 文档

---

## 目录

1. [常量](#常量)
2. [初始化与配置](#初始化与配置)
3. [Hook操作](#hook操作)
4. [类与方法处理](#类与方法处理)
5. [字段操作](#字段操作)
6. [事务管理](#事务管理)
7. [指令Hook](#指令hook)
8. [方法调用Hook](#方法调用hook)
9. [工具方法](#工具方法)
10. [Native Hook](#native-hook)
11. [使用示例](#使用示例)

---

## 常量

### 架构标识符
```java
public static final int kArm = 1;        // ARMv7架构
public static final int kArm64 = 2;       // ARMv8/ARM64架构
public static final int kX86 = 3;         // x86架构
public static final int kX86_64 = 4;      // x86_64架构
```

### 字段标志
```java
public static final int FLAG_FIELD_BACKUP_INSTANCE = 0x40;  // 启用实例字段备份
public static final int FLAG_FIELD_BACKUP_STATIC = 0x80;    // 启用静态字段备份
public static final int FLAG_FIELD_BACKUP_BAN = 0x100;      // 禁用字段备份
public static final int FLAG_FIELD_DISABLED = 0x200;        // 字段功能禁用
public static final int FLAG_FIELD_INVALID = FLAG_FIELD_BACKUP_BAN | FLAG_FIELD_DISABLED;
```

### 操作标志
```java
public static final int FLAG_INIT_CLASS = 0x1;           // 强制类初始化
public static final int FLAG_DEBUG = 0x2;                // 调试模式
public static final int FLAG_LOADER_FROM_CALLER = 0x4;   // 使用调用者的类加载器
public static final int FLAG_DISABLE_JIT = 0x8;          // 禁用JIT编译
public static final int FLAG_SUSPEND_VM = 0x10;          // 操作期间挂起VM
public static final int FLAG_NO_COMPILE = 0x20;          // 禁用编译
public static final int FLAG_DISABLE_LOG = 0x400;        // 禁用日志
public static final int FLAG_INJECT = 0x800;             // 注入模式
public static final int FLAG_INIT_RPC = 0x1000;          // 初始化RPC
public static final int FLAG_CALL_CHAIN = 0x2000;        // 调用链模式
public static final int FLAG_ANTI_DETECTION = 0x4000;     // 反检测
public static final int FLAG_INTERPRETER = 0x8000;        // 解释器模式（禁用编译，强制解释执行）
```

### 状态常量
```java
public final static int STATUS_INIT_OK = 1;      // 初始化成功
public final static int STATUS_DISABLED = 2;     // 已禁用
public final static int STATUS_NOT_INIT = 4;     // 未初始化
public final static int STATUS_INIT_FAIL = 8;    // 初始化失败
```

### 搜索标志
```java
public static final int SEARCH_STATIC = 1;       // 搜索静态方法
public static final int SEARCH_INSTANCE = 2;     // 搜索实例方法
public static final int SEARCH_ALL = SEARCH_STATIC | SEARCH_INSTANCE;  // 搜索所有方法
```

---

## 初始化与配置

### `init`
```java
public static boolean init(int flags);
```
**功能**: 初始化Albatross框架  
**参数**:
- `flags`: 初始化标志位组合  
**返回**: `true` 如果初始化成功，否则 `false`

### `loadLibrary`
```java
public static boolean loadLibrary(String library);
public static boolean loadLibrary(String library, int loadFlags);
```
**功能**: 加载本地库并初始化Albatross  
**参数**:
- `library`: 要加载的本地库名称
- `loadFlags`: 加载标志位组合  
**返回**: `true` 如果Albatross初始化成功

### `initRpcClass`
```java
public native static boolean initRpcClass(Class<?> clz);
```
**功能**: 初始化RPC类  
**参数**:
- `clz`: 需要RPC初始化的类  
**返回**: `true` 如果成功，否则 `false`

### `supportFeatures`
```java
public static String supportFeatures();
```
**功能**: 获取当前支持的功能列表  
**返回**: 支持的功能字符串，如 "jit,aot,instruction"

---

## Hook操作

### `backupAndHook`
```java
public static boolean backupAndHook(Member target, Method hook, Method backup) throws AlbatrossException;
```
**功能**: 备份并Hook一个方法  
**参数**:
- `target`: 目标方法或构造函数
- `hook`: Hook方法
- `backup`: 备份方法（可为null）  
**返回**: `true` 如果成功  
**抛出**: `AlbatrossException` 如果失败

### `backup`
```java
public static boolean backup(Member target, Method backup) throws AlbatrossException;
```
**功能**: 创建方法备份而不Hook  
**参数**:
- `target`: 目标方法或构造函数
- `backup`: 备份方法  
**返回**: `true` 如果成功

### `replace`
```java
public static boolean replace(Member target, Method hook) throws AlbatrossException;
```
**功能**: 替换方法而不创建备份  
**参数**:
- `target`: 目标方法或构造函数
- `hook`: 替换方法  
**返回**: `true` 如果成功

### `hookClass`
```java
public static int hookClass() throws AlbatrossErr;                                    // 以调用者类为Hooker
public static int hookClass(Class<?> hooker) throws AlbatrossErr;
public static int hookClass(Class<?> hooker, Class<?> defaultClass) throws AlbatrossErr;
public static int hookObject(Class<?> hooker, Object instance) throws AlbatrossErr;
public static int hookClass(Class<?> hooker, ClassLoader loader, Class<?> defaultClass, Object instance) throws AlbatrossErr;
```
**功能**: 应用Hooker类中定义的所有Hook  
**参数**:
- `hooker`: Hooker类
- `defaultClass`: 默认目标类
- `loader`: 类加载器
- `instance`: 目标实例  
**返回**: 成功Hook的数量（失败时返回 `REDUNDANT_ELEMENT`）

### `unhookClass` / `unhookMethod`
```java
public static int unhookClass(Class<?> hooker) throws AlbatrossErr;
public static int unhookClass(Class<?> hooker, Class<?> targetClass);
public static boolean unhookMethod(Member target, Method hook, Method backup);
```
**功能**: 撤销类或方法的Hook

### `convert`
```java
public static native <T> T convert(Object object, Class<T> hooker);
```
**功能**: 将对象转换为Hooker类型以进行方法/字段访问

---

## 类与方法处理

### `isCompiled`
```java
public static boolean isCompiled(Method method);
```
**功能**: 检查方法是否已编译为机器码

### `compileClass`
```java
public static int compileClass(Class<?> clazz, int compileOption);
```
**功能**: 使用指定编译策略编译类

### `compileMethod`
```java
public static boolean compileMethod(Member method);
```
**功能**: 将特定方法编译为机器码

### `setMethodExecMode`
```java
public static boolean setMethodExecMode(Member method, int execMode);
```
**功能**: 设置方法的执行模式

### `setExecConfiguration`
```java
public static void setExecConfiguration(int targetExecMode, int hookerExecMode);
public static void setExecConfiguration(int targetExecMode, int hookerExecMode, int hookerBackupExec);
```
**功能**: 设置执行配置  
**参数**:
- `targetExecMode`: 目标方法执行模式
- `hookerExecMode`: Hooker方法执行模式
- `hookerBackupExec`: Hooker备份方法执行模式

### `compileClassByAnnotation` / `setInlineMaxCodeUnits` / `getMethodCodeSize` / `getMethodHookCount`
```java
public static int compileClassByAnnotation(Class<?> clazz, int compileOption);
public static void setInlineMaxCodeUnits(int n);
public static native int getMethodCodeSize(Member method);
public static native int getMethodHookCount(Member method);
```
**功能**: 按注解编译类、设置内联上限、查询方法代码大小/Hook数量

### `decompileAll` / `decompileMethod` / `addDecompileMethod` / `preventMethodInlining`
```java
public static native void decompileAll();                                    // 反编译所有方法
public static native boolean decompileMethod(Member method, boolean allowInline);
public static boolean addDecompileMethod(Member target, Member hook, int dexPc);
public static boolean preventMethodInlining(Member method);
```
**功能**: 反编译与防止内联

### `disableCompileBackupCall`
```java
public static void disableCompileBackupCall();
```
**功能**: 禁用备份方法调用的编译

---

## 字段操作

### `backupField`
```java
public static boolean backupField(Field target, Field backup) throws FieldException, AlbatrossErr;
```
**功能**: 创建字段实现的备份

### `isFieldEnable`
```java
public static boolean isFieldEnable();
```
**功能**: 检查字段Hook是否启用（Android ≤7.1默认禁用）

### 字段备份控制
```java
public static void disableFieldBackup();  // 禁用字段备份
public static void enableAlbatross();     // 重新启用框架
public static void disableAlbatross();    // 禁用整个框架
```

---

## 事务管理

### `transactionBegin`
```java
public static int transactionBegin();
public static synchronized native int transactionBegin(boolean disableHidden);
```
**功能**: 开始Hook事务，用于批量处理

### `transactionEnd`
```java
public static int transactionEnd(boolean doTask);
public static int transactionEnd(boolean doTask, boolean suspendVM);
```
**功能**: 提交或回滚事务  
**参数**:
- `doTask`: 执行待处理的Hook
- `suspendVM`: 操作期间挂起VM

### `transactionLevel`
```java
public static synchronized native int transactionLevel();
```
**功能**: 返回当前事务嵌套级别

---

## 指令Hook

### `hookInstruction`
```java
public static boolean hookInstruction(Member member, int dexPc, InstructionListener listener);
public static boolean hookInstruction(Member member, int minDexPc, int maxDexPc, InstructionListener listener);
public static boolean hookInstruction(Member member, int minDexPc, int maxDexPc, InstructionListener listener, int compile);
```
**功能**: Hook方法指令执行，指令进入监听范围时触发回调  
**参数**:
- `member`: 目标方法
- `minDexPc`/`maxDexPc`: DEX PC范围
- `listener`: `InstructionListener` 指令回调（`onEnter`/`onReturn`，可通过 `invocationContext` 读写寄存器）
- `compile`: 编译选项  
**返回**: `true` 如果Hook成功  
**说明**: 首次调用时会自动初始化指令Hook（`insHookInit`）。取消Hook调用 `listener.unHook()`

### `insHookInit`
```java
public static void insHookInit();
```
**功能**: 手动初始化指令Hook（一般无需调用，`hookInstruction` 会自动初始化）

---

## 方法调用Hook

### `hookMethod`
```java
public static MethodCallHook hookMethod(Member member, MethodCallback callback, int compile);
```
**功能**: 方法调用级Hook——每次目标方法被调用时回调，无需声明Hooker类  
**参数**:
- `member`: 目标方法
- `callback`: `MethodCallback` 回调（`Object call(CallFrame)`）
- `compile`: 编译选项  
**返回**: `MethodCallHook` 实例，可调用 `unHook()` 取消

---

## 工具方法

### `addToVisit`
```java
public static boolean addToVisit(Class<?> clz);
```
**功能**: 注册类以便将来Hook

### `isMainThread`
```java
public static native boolean isMainThread();
```
**功能**: 检查当前线程是否为主应用线程

### `isHooked`
```java
public static native boolean isHooked(Class<?> clz);
```
**功能**: 验证类是否有活动的Hook

### `currentApplication`
```java
public static native Application currentApplication();
```
**功能**: 获取当前Application上下文

### `currentPackageName` / `currentProcessName` / `methodToString`
```java
public static native String currentPackageName();
public static native String currentProcessName();
public static native String methodToString(Member member);
```
**功能**: 获取当前包名/进程名，方法转字符串

### `currentInstrumentation` / `getMainHandler` / `getProfileFilePath` / `getThreadTid` / `getTid`
```java
public static Instrumentation currentInstrumentation();
public static Handler getMainHandler();
public static String getProfileFilePath();
public static native int getThreadTid(Thread thread);
public static native int getTid();
```
**功能**: 环境与线程信息（前三个经由内部 `ActivityThreadH` 镜像实现）

### `getCallerClass`
```java
public native static Class<?> getCallerClass();
```
**功能**: 获取调用者类

### `findClass`
```java
public static Class<?> findClass(String className);
public static Class<?> findClass(String[] className);
public static Class<?> findClass(String[] className, ClassLoader loader);
public static Class<?> findClassFromApplication(String className);
```
**功能**: 从所有类加载器中查找类

### `findMethod`
```java
public static native Method findMethod(Class<?> clz, Class<?>[] argTypes, int isStatic);
```
**功能**: 查找方法  
**参数**:
- `clz`: 目标类
- `argTypes`: 参数类型数组
- `isStatic`: 搜索标志（SEARCH_STATIC, SEARCH_INSTANCE, SEARCH_ALL）

### `getDeclaredMethods`
```java
public static native Method[] getDeclaredMethods(Class<?> clz, int isStatic);
```
**功能**: 获取声明的方法

### `searchMethodCaller` / `searchField` / `searchObject` 等搜索API
```java
public static int searchMethodCaller(Member method, SearchCallback<Member> callback, boolean pickFirst, int searchScope);
public static int searchMethodCaller(Class<?> clz, Member callee, SearchCallback<Member> callback, boolean pickFirst);
public static int searchMethodCallerFromClass(Member method, Class<?> clz, SearchCallback<Member> callback, boolean pickFirst);
public static int searchField(Field field, int operation, FieldCallback callback, boolean pickFirst, boolean searchPlatform);
public static int searchFieldClassRef(Field field, Class<?> clz, int operation, FieldCallback callback);
public static <T> int searchObject(Class<T> clz, SearchCallback<T> callback);
public static <T> List<T> searchObjects(Class<T> clz);
public static void searchBootClass(SearchClassCallback callback);
public static void searchApplicationClass(SearchClassCallback callback);
public static void searchClass(SearchClassCallback callback, int scope);
```
**功能**: 在堆/类加载器中搜索方法调用者、字段引用与对象实例

### `registerAlbNative` / `registerOceanTracker` / `drmSet`
```java
public static native boolean registerAlbNative(Class<?> AlbNative, Method m, Method enter, Method leave);
public static native boolean registerOceanTracker(Class<?> ocean);
public static synchronized native boolean drmSet(byte[] value);
```
**功能**: 注册native Hook回调（`AlbNative` 静态块自动调用）、ocean跟踪器、DRM设置

### `getRuntimeISA`
```java
public static native int getRuntimeISA();
```
**功能**: 获取运行时指令集架构  
**返回**: 当前CPU架构，如 `kArm`, `kArm64`, `kX86`, 或 `kX86_64`

### `getObjectAddress`
```java
public native static long getObjectAddress(Object object);
```
**功能**: 获取对象的内存地址

### `disableMethod`
```java
public static boolean disableMethod(Method method);
public static native boolean disableMethod(Method method, boolean throwException);
```
**功能**: 禁用方法

### `resetLogger`
```java
public static void resetLogger(Method infoLogger, Method errLogger);
```
**功能**: 重置日志器

### `disableLog`
```java
public synchronized static void disableLog();
```
**功能**: 禁用日志

---

## Native Hook

`qing.albatross.nativehook` 包提供了 native（C/C++）库Hook能力。

### `AlbNative`
```java
public class AlbNative {
  public static void watchFunc(String symbol, long func);                                    // 监控函数调用
  public static void hookInit(String logPath);                                               // 初始化native hook日志
  public static boolean dumpNativeMethod(String outputPath);                                 // 导出native方法信息
  public static void registerLibraryCallback(SearchCallback callback, String libName);       // 注册库加载回调
  public static void enumerateModules(SearchCallback callback);                              // 枚举已加载的so库
  public static DlInfo openLib(String libName);                                              // 打开动态库（dlopen）
  public static HookRecord hookInstruction(String lib, String function, InstructionCallback onEnter,
                                           InstructionCallback onLeave, Object userdata);   // 指令级Hook
  public static HookRecord hookInstruction(long symbolAddress, InstructionCallback onEnter,
                                           InstructionCallback onLeave, Object userdata);
  public static int hookNative();                                                           // 按注解Hook native函数
  public static int hookNative(Class<?> hooker, String lib);
}
```
**功能**: native Hook入口。`hookNative` 会根据 `@TargetLibrary`（加载so）、`@Symbol`（解析符号地址）和 `@FuncBackup`（备份native函数）注解自动完成Hook。

### `DlInfo`
```java
public class DlInfo {
  public long enumerateFunctions(SearchCallback callback);   // 枚举库内函数
  public long getSymbolAddress(String symbol);               // 获取符号地址
  public void close();                                       // 关闭库句柄
  public void backup(long address, Method method);           // 备份native函数（占位）
}
```

### `Address`
```java
public class Address {
  public static Address malloc(int size, boolean clear);     // 分配内存
  public void clear();                                      // 清零
  public void delete();                                     // 释放（free）
  public long getAddress();
  public long getSize();
  public String readString(int maxLen);                     // 读取字符串
  public boolean writeString(String str);                   // 写入字符串
}
```

### `NativeInvokeContext`
native指令Hook回调中读写参数/返回值：
```java
public class NativeInvokeContext {
  public boolean isJavaThread();
  public long getNthArgument(int nth);
  public <T> T getNthArgument(int nth, Class<T> clazz);   // 读对象参数
  public void setNthArgument(int nth, long value);
  public void setResult(long value);
  public long getResult();
}
```

### `NativeMethodParser` / `NativeMethodRecord`
解析native方法签名（`ART`寄存器/参数类型），`NativeMethodRecord(byte[] args, byte retType)` 保存解析结果。32位机上`long`默认按单字处理，可用`@Word64`标记为64位。

### `Libc`
libc 常用函数（`open`、`read`、`write`、`close`、`malloc`、`free` 等）的封装，可直接调用。

### `SearchCallback` / `InstructionCallback` / `HookRecord`
- `SearchCallback`：`boolean match(String symbol, long addr, long size, int idx)` 搜索回调
- `InstructionCallback`：`void onCall(NativeInvokeContext ctx, Object userdata)` 指令回调
- `HookRecord`：`unHook()` 取消native指令Hook

---

## 使用示例

### 示例1: 基本Hook
```java
@TargetClass(Activity.class)
public class ActivityHooker {
    @MethodHookBackup
    private void onCreate(Bundle savedInstanceState) {
        Log.d("Albatross", "Activity created!");
        onCreate(savedInstanceState);
    }
}

// 应用Hook
Albatross.hookClass(ActivityHooker.class);
```

### 示例2: 指令Hook
```java
Method targetMethod = MyClass.class.getDeclaredMethod("targetMethod");
boolean ok = Albatross.hookInstruction(targetMethod, 0, 10, new InstructionListener() {
    @Override
    public void onEnter(int dexPc, InvocationContext invocationContext) {
        Log.d("Albatross", "Instruction at dexPc: " + dexPc);
    }
});
```

### 示例3: 事务Hook
```java
Albatross.transactionBegin();
Albatross.setExecConfiguration(
    ExecutionOption.JIT_OPTIMIZED,
    ExecutionOption.JIT_OPTIMIZED
);
Albatross.hookClass(MyHooker.class);
Albatross.transactionEnd(true);
```

### 示例4: 字段访问
```java
@TargetClass(className = "com.example.TargetClass")
public class TargetHooker {
    @FieldRef
    static int mSecretField;

    public static void readSecretField(Object target) {
        if (Albatross.isFieldEnable()) {
            // 通过convert访问字段
            TargetHooker hooker = Albatross.convert(target, TargetHooker.class);
            int value = hooker.mSecretField;
            Log.d("Albatross", "Secret value: " + value);
        }
    }
}
```

### 示例5: Native Hook
```java
// 打开动态库并查找符号
DlInfo lib = AlbNative.openLib("libc.so");
if (lib != null) {
    long mallocAddr = lib.getSymbolAddress("malloc");
    Log.d("Albatross", "malloc address: 0x" + Long.toHexString(mallocAddr));
    lib.close();
}

// 按注解Hook native函数
@TargetLibrary("log")
class LiblogH {
    @Symbol("__android_log_print")
    static long logPrint;

    @FuncBackup("__android_log_print")
    private static native int logPrintBackup(int prio, String tag, String msg);
}
AlbNative.hookNative(LiblogH.class, "log");
```

---

## 重要说明

1. **字段Hook限制**:
   - Android ≤7.1默认禁用

2. **编译策略**:
   - 调试模式: 无优化,保持原有的执行方式
   - 发布模式: JIT/AOT,机器码执行

3. **架构支持**:
   - x86, x86_64, ARMv7, ARM64

4. **错误处理**:
   - 始终将Hook操作包装在try-catch中
   - 检查返回值以确认成功状态

5. **事务使用**:
   - 始终配对使用 `transactionBegin/End` 调用
   - 事务失败时会自动回滚

6. **性能优化**:
   - 使用事务进行批量Hook操作
   - 合理设置编译选项
---

本API文档提供了使用Albatross构建高级Hook场景的基础。要获得完整功能，请结合[注解参考文档](annotatin_reference_CN.md)中描述的注解系统使用这些方法。
