# Albatross API Documentation

---

## Table of Contents

1. [Constants](#constants)
2. [Initialization & Configuration](#initialization--configuration)
3. [Hook Operations](#hook-operations)
4. [Class & Method Handling](#class--method-handling)
5. [Field Operations](#field-operations)
6. [Transaction Management](#transaction-management)
7. [Instruction Hook](#instruction-hook)
8. [Method-Call Hook](#method-call-hook)
9. [Utility Methods](#utility-methods)
10. [Native Hook](#native-hook)
11. [Usage Examples](#usage-examples)

---

## Constants

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

### Operation Flags
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
public static final int FLAG_ANTI_DETECTION = 0x4000;     // Anti-detection
public static final int FLAG_INTERPRETER = 0x8000;        // Interpreter mode (disable compilation, force interpretation)
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
public static final int SEARCH_STATIC = 1;       // Search static methods
public static final int SEARCH_INSTANCE = 2;     // Search instance methods
public static final int SEARCH_ALL = SEARCH_STATIC | SEARCH_INSTANCE;  // Search all methods
```

---

## Initialization & Configuration

### `init`
```java
public static boolean init(int flags);
```
**Initializes the Albatross framework.**
**Parameters**:
- `flags`: combination of initialization flags
**Returns**: `true` if initialization succeeded, otherwise `false`

### `loadLibrary`
```java
public static boolean loadLibrary(String library);
public static boolean loadLibrary(String library, int loadFlags);
```
**Loads a native library and initializes Albatross.**
**Parameters**:
- `library`: name of the native library to load
- `loadFlags`: combination of load flags
**Returns**: `true` if Albatross initialized successfully

### `initRpcClass`
```java
public native static boolean initRpcClass(Class<?> clz);
```
**Initializes an RPC class.**
**Parameters**:
- `clz`: class requiring RPC initialization
**Returns**: `true` if successful, otherwise `false`

### `supportFeatures`
```java
public static String supportFeatures();
```
**Gets the list of supported features.**
**Returns**: a feature string such as "jit,aot,instruction"

---

## Hook Operations

### `backupAndHook`
```java
public static boolean backupAndHook(Member target, Method hook, Method backup) throws AlbatrossException;
```
**Backs up and hooks a method.**
**Parameters**:
- `target`: target method or constructor
- `hook`: hook method
- `backup`: backup method (may be null)
**Returns**: `true` if successful
**Throws**: `AlbatrossException` on failure

### `backup`
```java
public static boolean backup(Member target, Method backup) throws AlbatrossException;
```
**Creates a method backup without hooking.**
**Parameters**:
- `target`: target method or constructor
- `backup`: backup method
**Returns**: `true` if successful

### `replace`
```java
public static boolean replace(Member target, Method hook) throws AlbatrossException;
```
**Replaces a method without creating a backup.**
**Parameters**:
- `target`: target method or constructor
- `hook`: replacement method
**Returns**: `true` if successful

### `hookClass`
```java
public static int hookClass() throws AlbatrossErr;                                    // Uses the caller class as hooker
public static int hookClass(Class<?> hooker) throws AlbatrossErr;
public static int hookClass(Class<?> hooker, Class<?> defaultClass) throws AlbatrossErr;
public static int hookObject(Class<?> hooker, Object instance) throws AlbatrossErr;
public static int hookClass(Class<?> hooker, ClassLoader loader, Class<?> defaultClass, Object instance) throws AlbatrossErr;
```
**Applies all hooks defined in the hooker class.**
**Parameters**:
- `hooker`: hooker class
- `defaultClass`: default target class
- `loader`: class loader
- `instance`: target instance
**Returns**: the number of successfully applied hooks (or `REDUNDANT_ELEMENT` on failure)

### `unhookClass` / `unhookMethod`
```java
public static int unhookClass(Class<?> hooker) throws AlbatrossErr;
public static int unhookClass(Class<?> hooker, Class<?> targetClass);
public static boolean unhookMethod(Member target, Method hook, Method backup);
```
**Removes hooks** from a class or a single method.

### `convert`
```java
public static native <T> T convert(Object object, Class<T> hooker);
```
**Casts an object** to a hooker type for method/field access.

---

## Class & Method Handling

### `isCompiled`
```java
public static boolean isCompiled(Method method);
```
**Checks whether a method** is compiled to machine code.

### `compileClass`
```java
public static int compileClass(Class<?> clazz, int compileOption);
```
**Compiles a class** using the specified compilation strategy.

### `compileMethod`
```java
public static boolean compileMethod(Member method);
```
**Compiles a specific method** to machine code.

### `setMethodExecMode`
```java
public static boolean setMethodExecMode(Member method, int execMode);
```
**Sets the execution mode** of a method.

### `setExecConfiguration`
```java
public static void setExecConfiguration(int targetExecMode, int hookerExecMode);
public static void setExecConfiguration(int targetExecMode, int hookerExecMode, int hookerBackupExec);
```
**Sets the execution configuration.**
**Parameters**:
- `targetExecMode`: execution mode of the target method
- `hookerExecMode`: execution mode of the hooker method
- `hookerBackupExec`: execution mode of the hooker backup method

### `compileClassByAnnotation` / `setInlineMaxCodeUnits` / `getMethodCodeSize` / `getMethodHookCount`
```java
public static int compileClassByAnnotation(Class<?> clazz, int compileOption);
public static void setInlineMaxCodeUnits(int n);
public static native int getMethodCodeSize(Member method);
public static native int getMethodHookCount(Member method);
```
**Compiles a class by its annotations, sets the inline limit, and queries method code size / hook count.**

### `decompileAll` / `decompileMethod` / `addDecompileMethod` / `preventMethodInlining`
```java
public static native void decompileAll();                                    // Decompile all methods
public static native boolean decompileMethod(Member method, boolean allowInline);
public static boolean addDecompileMethod(Member target, Member hook, int dexPc);
public static boolean preventMethodInlining(Member method);
```
**Decompilation and inlining prevention.**

### `disableCompileBackupCall`
```java
public static void disableCompileBackupCall();
```
**Disables compilation of backup-method calls.**

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
**Checks whether field hooking is enabled** (disabled by default on Android ≤7.0).

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
**Commits or rolls back** a transaction.
**Parameters**:
- `doTask`: execute the pending hooks
- `suspendVM`: suspend the VM during the operation

### `transactionLevel`
```java
public static synchronized native int transactionLevel();
```
**Returns the current transaction nesting level.**

---

## Instruction Hook

### `hookInstruction`
```java
public static boolean hookInstruction(Member member, int dexPc, InstructionListener listener);
public static boolean hookInstruction(Member member, int minDexPc, int maxDexPc, InstructionListener listener);
public static boolean hookInstruction(Member member, int minDexPc, int maxDexPc, InstructionListener listener, int compile);
```
**Hooks the instruction execution of a method; the callback fires when execution enters the listened range.**
**Parameters**:
- `member`: target method
- `minDexPc`/`maxDexPc`: DEX PC range
- `listener`: `InstructionListener` callback (`onEnter`/`onReturn`; registers can be read/written through the `invocationContext`)
- `compile`: compilation option
**Returns**: `true` if the hook was applied
**Note**: the instruction hook is initialized automatically on first use (`insHookInit`). Cancel with `listener.unHook()`.

### `insHookInit`
```java
public static void insHookInit();
```
**Manually initializes the instruction hook** (normally unnecessary — `hookInstruction` does it automatically).

---

## Method-Call Hook

### `hookMethod`
```java
public static MethodCallHook hookMethod(Member member, MethodCallback callback, int compile);
```
**Method-call-level hook — the callback fires on every invocation of the target method, without declaring a hooker class.**
**Parameters**:
- `member`: target method
- `callback`: `MethodCallback` callback (`Object call(CallFrame)`)
- `compile`: compilation option
**Returns**: a `MethodCallHook` instance, cancelable via `unHook()`.

---

## Utility Methods

### `addToVisit`
```java
public static boolean addToVisit(Class<?> clz);
```
**Registers a class** for future hooking.

### `isMainThread`
```java
public static native boolean isMainThread();
```
**Checks whether the current thread** is the main application thread.

### `isHooked`
```java
public static native boolean isHooked(Class<?> clz);
```
**Verifies whether a class** has active hooks.

### `currentApplication`
```java
public static native Application currentApplication();
```
**Retrieves the current Application** context.

### `currentPackageName` / `currentProcessName` / `methodToString`
```java
public static native String currentPackageName();
public static native String currentProcessName();
public static native String methodToString(Member member);
```
**Gets the current package name / process name, and converts a method to a string.**

### `currentInstrumentation` / `getMainHandler` / `getProfileFilePath` / `getThreadTid` / `getTid`
```java
public static Instrumentation currentInstrumentation();
public static Handler getMainHandler();
public static String getProfileFilePath();
public static native int getThreadTid(Thread thread);
public static native int getTid();
```
**Environment and thread information** (the first three are implemented through the internal `ActivityThreadH` mirror).

### `getCallerClass`
```java
public native static Class<?> getCallerClass();
```
**Gets the caller class.**

### `findClass`
```java
public static Class<?> findClass(String className);
public static Class<?> findClass(String[] className);
public static Class<?> findClass(String[] className, ClassLoader loader);
public static Class<?> findClassFromApplication(String className);
```
**Finds a class** from all class loaders.

### `findMethod` / `getDeclaredMethods`
```java
public static native Method findMethod(Class<?> clz, Class<?>[] argTypes, int isStatic);
public static native Method[] getDeclaredMethods(Class<?> clz, int isStatic);
```
**Finds a method and gets the declared methods.**
**Parameters**:
- `clz`: target class
- `argTypes`: array of argument types
- `isStatic`: search flags (SEARCH_STATIC, SEARCH_INSTANCE, SEARCH_ALL)

### `searchMethodCaller` / `searchField` / `searchObject` and other search APIs
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
**Searches the heap / class loaders** for method callers, field references and object instances.

### `registerAlbNative` / `registerOceanTracker` / `drmSet`
```java
public static native boolean registerAlbNative(Class<?> AlbNative, Method m, Method enter, Method leave);
public static native boolean registerOceanTracker(Class<?> ocean);
public static synchronized native boolean drmSet(byte[] value);
```
**Registers native hook callbacks** (called automatically from the static block of `AlbNative`), the ocean tracker, and DRM settings.

### `getRuntimeISA`
```java
public static native int getRuntimeISA();
```
**Gets the runtime instruction set architecture.**
**Returns**: the current CPU architecture, e.g. `kArm`, `kArm64`, `kX86`, or `kX86_64`.

### `getObjectAddress`
```java
public native static long getObjectAddress(Object object);
```
**Gets the memory address** of an object.

### `disableMethod`
```java
public static boolean disableMethod(Method method);
public static native boolean disableMethod(Method method, boolean throwException);
```
**Disables a method.**

### `resetLogger`
```java
public static void resetLogger(Method infoLogger, Method errLogger);
```
**Resets the logger.**

### `disableLog`
```java
public synchronized static void disableLog();
```
**Disables logging.**

---

## Native Hook

The `qing.albatross.nativehook` package provides native (C/C++) library hooking capabilities.

### `AlbNative`
```java
public class AlbNative {
  public static void watchFunc(String symbol, long func);                                    // Watch a function
  public static void hookInit(String logPath);                                               // Init native-hook logging
  public static boolean dumpNativeMethod(String outputPath);                                 // Dump native methods
  public static void registerLibraryCallback(SearchCallback callback, String libName);       // Register library-load callback
  public static void enumerateModules(SearchCallback callback);                              // Enumerate loaded .so libraries
  public static DlInfo openLib(String libName);                                              // Open a dynamic library (dlopen)
  public static HookRecord hookInstruction(String lib, String function, InstructionCallback onEnter,
                                           InstructionCallback onLeave, Object userdata);   // Instruction-level hook
  public static HookRecord hookInstruction(long symbolAddress, InstructionCallback onEnter,
                                           InstructionCallback onLeave, Object userdata);
  public static int hookNative();                                                           // Hook native functions by annotations
  public static int hookNative(Class<?> hooker, String lib);
}
```
**Native hook entry point.** `hookNative` automatically completes the hook based on `@TargetLibrary` (loads the `.so`), `@Symbol` (resolves symbol addresses) and `@FuncBackup` (backs up native functions).

### `DlInfo`
```java
public class DlInfo {
  public long enumerateFunctions(SearchCallback callback);   // Enumerate functions in the library
  public long getSymbolAddress(String symbol);               // Get a symbol address
  public void close();                                       // Close the library handle
  public void backup(long address, Method method);           // Back up a native function (placeholder)
}
```

### `Address`
```java
public class Address {
  public static Address malloc(int size, boolean clear);     // Allocate memory
  public void clear();                                      // Zero-fill
  public void delete();                                     // Free
  public long getAddress();
  public long getSize();
  public String readString(int maxLen);                     // Read a string
  public boolean writeString(String str);                   // Write a string
}
```

### `NativeInvokeContext`
Reads/writes arguments and the return value inside native instruction-hook callbacks:
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
Parses native method signatures (`ART` registers/argument types); `NativeMethodRecord(byte[] args, byte retType)` stores the result. On 32-bit platforms `long` is treated as a single word by default; annotate with `@Word64` to force 64-bit handling.

### `Libc`
A wrapper around common libc functions (`open`, `read`, `write`, `close`, `malloc`, `free`, etc.) for direct use.

### `SearchCallback` / `InstructionCallback` / `HookRecord`
- `SearchCallback`: `boolean match(String symbol, long addr, long size, int idx)` search callback
- `InstructionCallback`: `void onCall(NativeInvokeContext ctx, Object userdata)` instruction callback
- `HookRecord`: `unHook()` cancels a native instruction hook

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
    public void onEnter(Member method, Object self, int dexPc, InvocationContext invocationContext) {
        Log.d("Albatross", "Instruction at dexPc: " + dexPc);
    }
});
```

### Example 3: Transaction Hook
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
            // Access the field through convert
            TargetHooker hooker = Albatross.convert(target, TargetHooker.class);
            int value = hooker.mSecretField;
            Log.d("Albatross", "Secret value: " + value);
        }
    }
}
```

### Example 5: Native Hook
```java
// Open a library and look up a symbol
DlInfo lib = AlbNative.openLib("libc.so");
if (lib != null) {
    long mallocAddr = lib.getSymbolAddress("malloc");
    Log.d("Albatross", "malloc address: 0x" + Long.toHexString(mallocAddr));
    lib.close();
}

// Hook native functions by annotations
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

1. **Field Hooking Limitations**:
   - Disabled by default on Android ≤7.1

2. **Compilation Strategies**:
   - Debug mode: no optimizations, keeps the original execution mode
   - Release mode: JIT/AOT, machine-code execution

3. **Architecture Support**:
   - x86, x86_64, ARMv7, ARM64

4. **Error Handling**:
   - Always wrap hook operations in try-catch
   - Check return values to confirm success

5. **Transaction Usage**:
   - Always pair `transactionBegin/End` calls
   - Transactions roll back automatically on failure

6. **Performance Optimization**:
   - Use transactions for batch hooking
   - Set compilation options wisely
   - Avoid frequent class lookups

---

This API documentation provides the foundation for building advanced hooking scenarios with Albatross. For full functionality, combine these methods with the annotation system described in the [Annotation Reference](annotatin_reference_EN.md).
