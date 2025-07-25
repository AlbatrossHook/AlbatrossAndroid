# Albatross Annotation Reference
Albatross is a annotation-driven Android hooking and reflection framework designed to dynamically modify the behavior of classes and methods at runtime. It provides a declarative approach to hooking via Java annotations, allowing developers to define **hookers** — mirror classes that represent the target class being hooked. Albatross supports multiple execution strategies including **JIT (Just-In-Time)** compilation, **AOT (Ahead-Of-Time)** compilation, and **interpreted mode**, which can be controlled through  configuration options.

---


## Core Concepts

### 1. Hooker
A **hooker** is a class that mirrors a target class and contains the logic for hooking or backing up its methods and fields. It serves as a proxy to intercept and modify the behavior of the original class.

#### Responsibilities of a Hooker:
- Define replacement logic for target methods via `@MethodHook`, `@StaticMethodHook`, or `@ConstructorHook`
- Define backup logic for original behavior via `@MethodBackup`, `@StaticMethodBackup`, or `@ConstructorBackup`
- Access and manipulate fields of the target class using `@FieldRef`
- **Execution configuration** to control how code is compiled and executed (JIT/AOT/Interpreted)

### 2. TargetClass
The **target class** is the class being hooked. It is defined using the `@TargetClass` annotation on the hooker class or manually passed to the `hookClass(hooker, targetClass)` method.


#### `@TargetClass` Attributes:
| Attribute        | Type            | Description |
|------------------|-----------------|-------------|
| `value()`        | `Class<?>`      | The target class type |
| `className()`    | `String[]`      | Array of class names to match (for inaccessible or lazy-loaded classes) |
| `pendingHook()`  | `boolean`       | If true, the hook is deferred until the class is loaded |
| `hookerExec()`| `int`           | Execution strategy for the hooker itself |
| `targetExec()`| `int`           | Execution strategy for the target class |
| `required()`     | `boolean`       | If true, an error is thrown if the class is not found |

---


## Hooking and Backup Annotations

Albatross provides a rich set of annotations to define hooking and backup logic for methods and constructors.

### Method Hooking
| Annotation              | Description |
|-------------------------|-------------|
| `@MethodHook`           | Replaces the target method |
| `@MethodBackup`         | Backs up the original method |
| `@MethodHookBackup`     | Combination of `@MethodHook` and `@MethodBackup` |
| `@StaticMethodHook`     | Replaces a static method |
| `@StaticMethodBackup`   | Backs up a static method |
| `@StaticMethodHookBackup` | Combination of `@StaticMethodHook` and `@StaticMethodBackup` |
| `@ConstructorHook`      | Replaces a constructor |
| `@ConstructorBackup`    | Backs up a constructor |
| `@ConstructorHookBackup`| Combination of `@ConstructorHook` and `@ConstructorBackup` |

### Common Annotation Attributes:
| Attribute        | Type            | Description                                            |
|------------------|-----------------|--------------------------------------------------------|
| `name()`         | `String[]`      | Names of the methods to hook                           |
| `value()`        | `String[]`      | Arguments classNames                                   |
| `isStatic()`     | `boolean`       | Indicates if the method is static                      |
| `targetClass()`  | `Class<?>`      | Explicitly specify the target class                    |
| `className()`    | `String[]`      | Class names for deferred or inaccessible classes       |
| `required()`     | `boolean`       | If true, an error is thrown if the method is not found |
| `hookerExec()`| `int`           | Execution strategy for the hook method               |
| `targetExec()`| `int`           | Execution strategy for the original method           |
| `option()`       | `int`           | Hooking behavior option (e.g., `DefOption.VIRTUAL`)    |
| `minSdk()`, `maxSdk()` | `int`     | SDK version constraints for hooking                    |

---


## Field Access

Use `@FieldRef` to access fields of the target class from the hooker.

### `@FieldRef` Attributes:
| Attribute        | Type            | Description                                           |
|------------------|-----------------|-------------------------------------------------------|
| `value()`        | `String[]`      | Names of the fields to access                         |
| `targetClass()`  | `Class<?>`      | Explicitly specify the target class                   |
| `className()`    | `String[]`      | Class names for deferred or inaccessible classes      |
| `required()`     | `boolean`       | If true, an error is thrown if the field is not found |
| `option()`       | `int`           | Resolves  option (e.g., `DefOption.VIRTUAL`)  |

---


##  Execution Options (`ExecOption`)
Albatross allows fine-grained control over how hooker and target code is executed at runtime using bit flags.
### Supported Compile Options
| Flag                     | Description                    |
|--------------------------|--------------------------------|
| `DO_NOTHING`           | No compilation                 |
| `DEFAULT_OPTION`        | Use default execution strategy |
| `JIT_OSR`            | On-Stack Replacement (OSR)     |
| `JIT_BASELINE`       | Baseline compilation           |
| `JIT_OPTIMIZED`      | Optimized compilation          |
| `INTERPRETER`      | Use interpreter mode               |
| `COMPILE_DISABLE_AOT`    | Disable AOT code               |
| `COMPILE_DISABLE_JIT`    | Disable JIT compilation        |
| `COMPILE_AOT`            | Use AOT                        |

### Common Combinations:
| Combination               | Description               |
|---------------------------|---------------------------|
| `RECOMPILE_OSR`         | Jit recompile + OSR       |
| `RECOMPILE_BASELINE`    | Jit recompile + Baseline  |
| `RECOMPILE_OPTIMIZED`   | Jit recompile + Optimized |

---


## Definition Options (`DefOption`)

Controls how Albatross resolves methods and fields in the target class.

| Option        | Description |
|---------------|-------------|
| `DEFAULT`     | Use default resolution strategy |
| `NOTHING`     | Do not hook or backup |
| `VIRTUAL`     | Resolve from parent classes if needed |
| `INSTANCE`    | Use instance to determine field type |

---


## Parameter Matching

Albatross supports advanced parameter matching via annotations:

### `@ParamInfo`
Used to explicitly specify the class name of a parameter when type inference is not possible.

```java
void someMethod(@ParamInfo("com.example.MyClass") Object obj);
```

### `@SubType`
Indicates that the parameter can be a subclass of the declared type.

```java
void someMethod(@SubType MyClass obj);
```

---


## Error Handling and Required Flags

- **AlbatrossErr**: Indicates a structural or critical error in the hooker class.
- **RequiredErr**: Thrown if a required field/method/class is not found and `required=true`.

### Error Handling Strategy:
- If `required = true` and the element is missing → throw exception and abort hooking
- If `required = false` and the element is missing → log warning and continue

---


## Getting Started

### Step 1: Define a Hooker Class

```java
@TargetClass(Activity.class)
public class ActivityH {

  //Active by ActivityH automatically
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

### Step 2: Apply Hooking Logic

```java
try {
    Albatross.hookClass(ActivityH.class);
} catch (AlbatrossErr e) {
    Log.e("Albatross", "Hook failed: " + e.getMessage());
}
```


