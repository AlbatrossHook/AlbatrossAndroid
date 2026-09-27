# Albatross API Documentation

---

## Table of Contents

1. [Constants](#constants)
2. [Initialization & Configuration](#initialization--configuration)
3. [Hook Operations](#hook-operations)
4. [Class & Method Handling](#class--method-handling)
5. [Field Operations](#field-operations)
6. [Transaction Management](#transaction-management)
7. [Utility & Environment](#utility--environment)
8. [Native Hook (v3.6.0+)](#native-hook-v360)
9. [Usage Examples](#usage-examples)

---

## Constants

All constants below are declared on `qing.albatross.core.Albatross`.

### Architecture Identifiers
```java
public static final int kArm = 1;        // ARMv7 architecture
public static final int kArm64 = 2;       // ARMv8/ARM64 architecture
public static final int kX86 = 3;         // x86 architecture
public static final int kX86_64 = 4;      // x86_64 architecture
```

### Field Flags
```java
public static final int FLAG_FIELD_BACKUP_INSTANCE = 0x40;  // Enable instance field backup
public static final int FLAG_FIELD_BACKUP_STATIC = 0x80;    // Enable static field backup
public static final int FLAG_FIELD_BACKUP_BAN = 0x100;      // Disable field backup
public static final int FLAG_FIELD_DISABLED = 0x200;        // Field feature disabled
public static final int FLAG_FIELD_INVALID = FLAG_FIELD_BACKUP_BAN | FLAG_FIELD_DISABLED;
```

### Operation Flags (passed to `init` / `loadLibrary`)
```java
public static final int FLAG_INIT_CLASS = 0x1;           // Force class initialization
public static final int FLAG_DEBUG = 0x2;                // Debug mode
public static final int FLAG_LOADER_FROM_CALLER = 0x4;   // Use caller's class loader
public static final int FLAG_DISABLE_JIT = 0x8;          // Disable JIT compilation
public static final int FLAG_SUSPEND_VM = 0x10;          // Suspend VM during operation
public static final int FLAG_NO_COMPILE = 0x20;          // Disable compilation
public static final int FLAG_DISABLE_LOG = 0x400;        // Disable logging
public static final int FLAG_INJECT = 0x800;             // Injection mode
public static final int FLAG_INIT_RPC = 0x1000;          // Initialize RPC
public static final int FLAG_CALL_CHAIN = 0x2000;        // Call-chain mode
public static final int FLAG_ANTI_DETECTION = 0x4000;    // Anti-detection
public static final int FLAG_INTERPRETER = 0x8000;       // Interpreter mode (disable compilation, force interpretation)
```

### Status Constants
```java
public final static int STATUS_INIT_OK = 1;      // Initialization succeeded
public final static int STATUS_DISABLED = 2;     // Disabled
public final static int STATUS_NOT_INIT = 4;     // Not initialized
public final static int STATUS_INIT_FAIL = 8;    // Initialization failed
```

### Search Flags
```java
public static final int SEARCH_STATIC = 1;        // Search static methods
public static final int SEARCH_INSTANCE = 2;      // Search instance methods
public static final int SEARCH_ALL = SEARCH_STATIC | SEARCH_INSTANCE;
```

### Special Return Values
```java
public static final int CLASS_ALREADY_HOOK = -1000000000;  // Class already hooked
public static final int REDUNDANT_ELEMENT = -1000000001;   // Redundant element (hook failed)
```

---

## Initialization & Configuration

### `init`
```java
public static boolean init(int flags);
```
**Initializes the Albatross framework.** Performs the native initialization, backs up `ActivityThread` methods and registers callbacks.
- `flags`: combination of `FLAG_*` operation flags
- **Returns**: `true` on success

### `loadLibrary`
```java
public static boolean loadLibrary(String library);
public static boolean loadLibrary(String library, int loadFlags);
```
**Loads a native library and initializes Albatross.**
- `library`: name of the native library to load
- `loadFlags`: combination of `FLAG_*` flags

### `initRpcClass`
```java
public native static boolean initRpcClass(Class<?> clz);
```
**Initializes an RPC class** for the server.

### `supportFeatures`
```java
public static String supportFeatures();
```
**Returns the supported feature string** (e.g. `"jit,aot,jit_backupCall,instruction"`).

### `containsFlags` / `ensureClassInitialized`
```java
public static boolean containsFlags(int flags);
public static boolean ensureClassInitialized(Class<?> clz);
```
**Contains flags check** and **forced class initialization**.

### `setExecConfiguration`
```java
public static void setExecConfiguration(int targetExecMode, int hookerExecMode);
public static void setExecConfiguration(int targetExecMode, int hookerExecMode, int hookerBackupExec);
```
**Sets execution strategies** for target methods, hooker methods and hooker backup methods using `ExecutionOption` constants.

### `disableCompile` / `disableCompileBackupCall` / `setInlineMaxCodeUnits`
```java
public static void disableCompile();
public static void disableCompileBackupCall();
public static void setInlineMaxCodeUnits(int n);
```
**Disables compilation** (globally / for backup calls) and **sets the inline code-unit limit**.

---

## Hook Operations

### `replace`
```java
public static boolean replace(Member target, Method hook) throws AlbatrossException;
```
**Replaces a method** without creating a backup.

### `backupAndHook`
```java
public static boolean backupAndHook(Member target, Method hook, Method backup) throws AlbatrossException;
public static boolean backupAndHook(Member target, Method hook, Method backup, boolean check, boolean checkReturn,
                                    Set<Class<?>> dependencies, int targetExecMode, int hookerExecMode) throws AlbatrossException;
```
**Replaces a method** while preserving the original implementation in a backup method.
**Throws**: `AlbatrossException` on failure

### `backup`
```java
public static boolean backup(Member target, Method backup) throws AlbatrossException;
```
**Creates a backup** of a method without hooking it.

### `hookClass` / `hookObject`
```java
public static int hookClass() throws AlbatrossErr;                                  // Uses the caller class as hooker
public static int hookClass(Class<?> hooker) throws AlbatrossErr;
public static int hookClass(Class<?> hooker, Class<?> defaultClass) throws AlbatrossErr;
public static int hookObject(Class<?> hooker, Object instance) throws AlbatrossErr;
public static int hookClass(Class<?> hooker, ClassLoader loader, Class<?> defaultClass, Object instance) throws AlbatrossErr;
```
**Applies all hooks** defined in a `@TargetClass` annotated hooker class.
**Returns**: the number of successfully applied hooks, or `REDUNDANT_ELEMENT` on failure.

### `unhookClass` / `unhookMethod`
```java
public static int unhookClass(Class<?> hooker) throws AlbatrossErr;
public static int unhookClass(Class<?> hooker, Class<?> targetClass);
public static boolean unhookMethod(Member target, Method hook, Method backup);
```
**Removes hooks** from a class or a single method.

### `addAssignableHooker`
```java
public static boolean addAssignableHooker(Class<?> hooker, Class<?> targetClass);
```
**Registers a hooker** for all assignable (subclass) targets of `targetClass`.

### `hookMethod` — Method-Call Hook
```java
public static MethodCallHook hookMethod(Member member, MethodCallback callback, int compile);
```
**Hooks every call of the target method** without declaring a hooker class. The returned `MethodCallHook` can be canceled via `unHook()`.

### `hookInstruction` — Instruction Hook
```java
public static boolean hookInstruction(Member member, int dexPc, InstructionListener listener);
public static boolean hookInstruction(Member member, int minDexPc, int maxDexPc, InstructionListener listener);
public static boolean hookInstruction(Member member, int minDexPc, int maxDexPc, InstructionListener listener, int compile);
```
**Hooks the execution of instructions** within the given dexPc range. The `InstructionListener` receives `onEnter`/`onReturn` callbacks and can read/write registers through the `InvocationContext`. Cancel via `listener.unHook()`.

### `insHookInit`
```java
public static void insHookInit();
```
**Manually initializes the instruction hook** — normally unnecessary, `hookInstruction` does it automatically.

### `convert`
```java
public static native <T> T convert(Object object, Class<T> hooker);
```
**Casts an object** to a hooker class type for method/field access.

---

## Class & Method Handling

### Compilation
```java
public static boolean isCompiled(Method method);                                    // Check if compiled to machine code
public static int compileClass(Class<?> clazz, int compileOption);                  // Compile a class
public static int compileClassByAnnotation(Class<?> clazz, int compileOption);      // Compile a class by its annotations
public static boolean compileMethod(Member method);                                 // Compile a single method
public static boolean setMethodExecMode(Member method, int execMode);               // Set execution mode of a method
```

### Decompilation
```java
public static native void decompileAll();                              // Decompile all methods
public static native boolean decompileMethod(Member method, boolean allowInline);
public static boolean addDecompileMethod(Member target, Member hook, int dexPc);
public static boolean preventMethodInlining(Member method);
```

### Method Inspection
```java
public static native int getMethodCodeSize(Member method);
public static native int getMethodHookCount(Member method);
public static native long entryPointFromQuickCompiledCode(Member method);
public static native long entryPointAddress(Member method);
public static native String methodToString(Member member);
public static native Method findMethod(Class<?> clz, Class<?>[] argTypes, int isStatic);
public static native Method[] getDeclaredMethods(Class<?> clz, int isStatic);
```
`isStatic` uses `SEARCH_STATIC` / `SEARCH_INSTANCE` / `SEARCH_ALL`.

### `disableMethod`
```java
public static boolean disableMethod(Method method);
public static native boolean disableMethod(Method method, boolean throwException);
```

---

## Field Operations

### `backupField`
```java
public static boolean backupField(Field target, Field backup) throws FieldException, AlbatrossErr;
```
**Creates a backup** of a field implementation.

### `isFieldEnable`
```java
public static boolean isFieldEnable();
```
**Checks whether field hooking is enabled** (disabled when `FLAG_FIELD_INVALID` is set).

### Field Backup Control
```java
public static void disableFieldBackup();  // Disable field backup
public static void enableAlbatross();     // Re-enable the framework
public static void disableAlbatross();    // Disable the whole framework
```

---

## Transaction Management

### `transactionBegin`
```java
public static int transactionBegin();
public static synchronized native int transactionBegin(boolean disableHidden);
```
**Starts a hook transaction** for batch processing.

### `transactionEnd`
```java
public static int transactionEnd(boolean doTask);
public static int transactionEnd(boolean doTask, boolean suspendVM);
```
**Commits or rolls back** the transaction.
- `doTask`: execute the pending hooks
- `suspendVM`: suspend the VM during the operation

### `transactionLevel`
```java
public static synchronized native int transactionLevel();
```
**Returns the current transaction nesting level.**

---

## Utility & Environment

```java
public static native Application currentApplication();          // Current Application context
public static native String currentPackageName();               // Current package name
public static native String currentProcessName();               // Current process name
public static Instrumentation currentInstrumentation();         // Current Instrumentation
public static Handler getMainHandler();                         // Main thread handler
public static String getProfileFilePath();                      // Profile file path
public static native boolean isMainThread();                    // Is current thread the main thread
public static native int getTid();                              // Current thread id
public static native int getThreadTid(Thread thread);           // Id of a given thread
public static native boolean isHooked(Class<?> clz);            // Does the class have active hooks
public static native Class<?> getCallerClass();                  // Caller class of the current method
public static native int getRuntimeISA();                       // Current ISA (kArm/kArm64/kX86/kX86_64)
public static native long getObjectAddress(Object object);      // Heap address of an object
public static Class<?> findClass(String className);             // Find a class across all class loaders
public static Class<?> findClass(String[] className);
public static Class<?> findClass(String[] className, ClassLoader loader);
public static Class<?> findClassFromApplication(String className);
public static void appendLoader(ClassLoader loader);            // Append a class loader
public static List<ClassLoader> getClassLoaderList();           // All known class loaders
public static void syncAppClassLoader();
public static void log(String msg);                             // Custom log
public static void log(String msg, Throwable tr);
public static void resetLogger(Method infoLogger, Method errLogger);
public synchronized static void disableLog();                   // Disable logging
```

### Search APIs
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
public static boolean classDexFileRefClass(Class<?> clz, Class<?> toRef);
```
Search the heap / class loaders for method callers, field references and object instances.

### Native Registration
```java
public static native boolean registerAlbNative(Class<?> AlbNative, Method m, Method enter, Method leave);
public static native boolean registerOceanTracker(Class<?> ocean);
public static synchronized native boolean drmSet(byte[] value);
```
`registerAlbNative` is called automatically from the static block of `AlbNative`.

### Built-in Mirrors (`Albatross` inner classes)
```java
@TargetClass public static final class LoadedApk { @FieldRef public ApplicationInfo mApplicationInfo; }
@TargetClass public static final class AppBindData {
  @FieldRef public LoadedApk info;
  @FieldRef public ApplicationInfo appInfo;
}
@TargetClass(className = "android.app.ActivityThread", targetExec = DO_NOTHING)
public static class ActivityThreadH { /* getInstrumentation, currentActivityThread, getHandler, mBoundApplication, ... */ }
```
These mirror classes back up `ActivityThread` internals; the convenience methods above (`currentApplication`, `currentInstrumentation`, `getMainHandler`, ...) are implemented through them.

---

## Native Hook (v3.6.0+)

The `qing.albatross.nativehook` package provides C/C++ library hooking support.

### `AlbNative`
```java
public class AlbNative {
  public static void watchFunc(String symbol, long func);                              // Watch a function
  public static void hookInit(String logPath);                                         // Init native-hook logging
  public static boolean dumpNativeMethod(String outputPath);                           // Dump native methods
  public static void registerLibraryCallback(SearchCallback callback, String libName); // Library-load callback
  public static void enumerateModules(SearchCallback callback);                        // Enumerate loaded .so
  public static DlInfo openLib(String libName);                                        // dlopen
  public static HookRecord hookInstruction(String lib, String function, InstructionCallback onEnter,
                                           InstructionCallback onLeave, Object userdata);
  public static HookRecord hookInstruction(long symbolAddress, InstructionCallback onEnter,
                                           InstructionCallback onLeave, Object userdata);
  public static int hookNative();                                                     // Hook natives by annotations
  public static int hookNative(Class<?> hooker, String lib);
}
```
`hookNative` processes the hooker annotations: `@TargetLibrary` (load the `.so`), `@Symbol` (fill `long` fields with `dlsym` addresses) and `@FuncBackup` (back up native functions).

### `DlInfo`
```java
public class DlInfo {
  public long enumerateFunctions(SearchCallback callback);
  public long getSymbolAddress(String symbol);
  public void close();
  public void backup(long address, Method method);
}
```

### `Address`
```java
public class Address {
  public static Address malloc(int size, boolean clear);   // Allocate memory
  public void clear();                                    // Zero-fill
  public void delete();                                   // Free
  public long getAddress();
  public long getSize();
  public String readString(int maxLen);                   // Read a C string
  public boolean writeString(String str);                 // Write a C string
}
```

### `Libc`
A wrapper of common libc functions (`open`, `read`, `write`, `close`, `malloc`, `free`, `mmap`, ...) for direct use.

### `NativeInvokeContext`
Read/write arguments and the return value inside a native instruction callback:
```java
public class NativeInvokeContext {
  public boolean isJavaThread();
  public long getNthArgument(int nth);
  public <T> T getNthArgument(int nth, Class<T> clazz);   // Read an object argument
  public void setNthArgument(int nth, long value);
  public void setResult(long value);
  public long getResult();
}
```

### `NativeMethodParser` / `NativeMethodRecord`
`NativeMethodParser.parseMethod(Method)` resolves the native method signature into argument/return types (`NativeMethodRecord(byte[] args, byte retType)`). On 32-bit platforms `long` is treated as a single word by default; annotate the parameter/method with `@Word64` to force 64-bit handling.

### Callbacks & Records
- `SearchCallback` — `boolean match(String symbol, long addr, long size, int idx)`
- `InstructionCallback` — `void onCall(NativeInvokeContext ctx, Object userdata)`
- `HookRecord` — `void unHook()` cancels a native instruction hook

---

## Usage Examples

### Example 1: Basic Hook
```java
@TargetClass(Activity.class)
public class ActivityHooker {
    @MethodHookBackup
    private void onCreate(Bundle savedInstanceState) {
        Log.d("Albatross", "Activity created!");
        onCreate(savedInstanceState);
    }
}

// Apply the hook
Albatross.hookClass(ActivityHooker.class);
```

### Example 2: Instruction Hook
```java
Method targetMethod = MyClass.class.getDeclaredMethod("targetMethod");
boolean ok = Albatross.hookInstruction(targetMethod, 0, 10, new InstructionListener() {
    @Override
    public void onEnter(int dexPc, InvocationContext invocationContext) {
        Log.d("Albatross", "Instruction at dexPc: " + dexPc);
    }
});
```

### Example 3: Transaction with Execution Configuration
```java
Albatross.transactionBegin();
Albatross.setExecConfiguration(
    ExecutionOption.JIT_OPTIMIZED,
    ExecutionOption.JIT_OPTIMIZED
);
Albatross.hookClass(MyHooker.class);
Albatross.transactionEnd(true);
```

### Example 4: Field Access
```java
@TargetClass(className = "com.example.TargetClass")
public class TargetHooker {
    @FieldRef
    static int mSecretField;

    public static void readSecretField(Object target) {
        if (Albatross.isFieldEnable()) {
            TargetHooker hooker = Albatross.convert(target, TargetHooker.class);
            int value = hooker.mSecretField;
            Log.d("Albatross", "Secret value: " + value);
        }
    }
}
```

### Example 5: Native Hook (v3.6.0+)
```java
// Open a library and look up a symbol
DlInfo lib = AlbNative.openLib("libc.so");
if (lib != null) {
    long mallocAddr = lib.getSymbolAddress("malloc");
    Log.d("Albatross", "malloc address: 0x" + Long.toHexString(mallocAddr));
    lib.close();
}

// Hook a native function by annotations
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

## Important Notes

1. **Field Hooking Limitations**: requires JIT; disabled by default on Android ≤ 7.x.
2. **Compilation Strategies**: debug mode keeps the original execution; release mode uses JIT/AOT machine code.
3. **Architecture Support**: x86, x86_64, ARMv7, ARM64.
4. **Error Handling**: always wrap hook operations in try-catch and check return values.
5. **Transactions**: always pair `transactionBegin`/`transactionEnd`.
6. **Interpreter Mode**: pass `FLAG_INTERPRETER` to `init` to disable compilation and force interpretation.

---

This API documentation provides the foundation for building advanced hooking scenarios with Albatross. For full functionality, combine these methods with the annotation system described in [docs/annotatin_reference_EN.md](docs/annotatin_reference_EN.md) and the exception handling in [docs/exception_documentation_EN.md](docs/exception_documentation_EN.md).
