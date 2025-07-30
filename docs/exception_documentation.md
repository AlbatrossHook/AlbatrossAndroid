# Albatross Exception Documentation
This document describes the updated exception hierarchy and error handling logic in Albatross framework, focusing on structural validation and required element checks during hooking operations.

Proper exception handling ensures robust hooker implementations and quick issue resolution.

## Exception Hierarchy

All exceptions inherit from the base `AlbatrossException` class:

```
AlbatrossException
├── AlbatrossErr (base class for critical hooker errors)
│   ├── RequiredErr (required element not found)
│   │   ├── RequiredFieldErr
│   │   ├── RequiredMethodErr
│   │   │── RequiredClassErr
│   │   └── RequiredInstanceErr
│   └── HookerStructErr (hooker structure violations)
│       ├── RedundantFieldErr
│       ├── FindRedundantMethodErr
│       ├── VirtualCallBackupErr
│       └── NotNativeBackupErr
|      
├── MethodException (method signature issues)
└── FieldException (field validation errors)
```
---



## Core Exceptions

### 1. `AlbatrossErr`
**Base class for critical hooker validation errors**  
All exceptions extending this type will **stop hooking process** when thrown.

- **Subclasses**:
  - `RequiredErr` - Required element missing
  - `HookerStructErr` - Invalid hooker structure

---

### 2. `RequiredErr`
**Thrown when required elements cannot be resolved**  
Only triggered when `required=true` in annotations.

| Subclass              | Trigger Condition         |
|-----------------------|---------------------------|
| `RequiredFieldErr`    | Required field not found  |
| `RequiredMethodErr`   | Required method not found |
| `RequiredClassErr`    | Target class not found    |
 | `RequiredInstanceErr` |  No instance object passed |

**Example**:
```java
@TargetClass(name = "com.example.NonExistentClass", required = true)
public class BadHooker {} 
// → Throws RequiredClassErr
```

---

### 3. `HookerStructErr`
**Base class for structural violations**  
Indicates invalid hooker design patterns.

#### Subclasses:
- `RedundantFieldErr` - Unused instance field
- `FindRedundantMethodErr` - Unused instance method
- `VirtualCallBackupErr` - Backup method not private
- `NotNativeBackupErr` - Backup method not native


**Validation Rules**:
- Hooker must only contain annotated instance fields/methods
- Backup instance methods must be private and native.


####  `RedundantFieldErr`
**Thrown when a hooker class contains unannotated instance fields**

- **Constructor**:
  ```java
  public RedundantFieldErr(Field f)
  ```
    - `f`: Redundant field reference

- **Error Example**:
  ```java
  @TargetClass(String.class)
  public class StringHooker {
      // Unannotated field → RedundantFieldErr
      public int unusedField;
  }
  ```

- **Fix**:
    - Remove unused fields
    - Mark as `static` if no instance association needed

---

####  `FindRedundantMethodErr`
**Thrown when a hooker class contains unannotated instance methods**

- **Constructor**:
  ```java
  public FindRedundantMethodErr(Method method)
  ```
    - `method`: Redundant method reference

- **Error Example**:
  ```java
  @TargetClass(Activity.class)
  public class ActivityHooker {
      // Instance method → FindRedundantMethodErr
      public void unusedMethod() {}
  }
  ```

- **Fix**:
    - Remove unused instance methods
    - Mark as `static`.

---

#### `VirtualCallBackupErr`
**Thrown when backup instance methods are not declared as private**

- **Constructor**:
  ```java
  public VirtualCallBackupErr(Method method)
  ```
    - `method`: Invalid backup method reference

- **Error Example**:
  ```java
  @MethodHookBackup
  public  void onCreate$Hook(Bundle savedInstanceState) {
    //....   hook logic 
    onCreate$Hook(savedInstanceState);//VirtualCallBackupErr
  }
  ```

- **Fix**:
    - Mark backup methods as `private`
    - Change method to static method
  ```java
    @MethodHookBackup
    private static void onCreate(Activity activity, Bundle savedInstanceState) {
        // hook implementation
      onCreate(activity,savedInstanceState)
    }
  ```

---


#### `NotNativeBackupErr`
**Thrown when backup instance methods are not declared as native**

- **Constructor**:
  ```java
  public NotNativeBackupErr(Method method)
  ```
  - `method`: Invalid backup method reference

- **Error Example**:
  ```java
  @MethodBackup
  private  void onCreate$Backup(Bundle savedInstanceState) {
    //....    
  }
  ```

- **Fix**:
  - Mark backup methods as `native`
  ```java
    @MethodBackup
  private  native void onCreate$Backup(Bundle savedInstanceState);
  ```

---

### 5. `MethodException`
**Indicates method signature mismatches**

#### Error Codes (`MethodException`)
| Code                     | Description                        |
|--------------------------|------------------------------------|
| `WRONG_ARGUMENT`         | Parameter type mismatch            |
| `WRONG_RETURN`           | Return type mismatch               |
| `ARGUMENT_SIZE_NOT_MATCH` | Parameter count mismatch          |

- **Scenarios**:
    - Mismatched parameter types/counts
    - Invalid return type declarations
    - Missing `static` modifiers

- **Resolution**:
    - Use `@ParamInfo` for explicit signature definitions
    - Match target method's signature exactly

---

### 5. `FieldException`
**Field validation errors**

| Reason               | Description                              |
|----------------------|------------------------------------------|
| `WRONG_STATIC_FIELD` | Mismatched static status between fields  |
| `WRONG_TYPE`         | Type mismatch between fields             |

---

## API Exception Contracts

### `hookClass(Class<?> hooker)`
```java
public static int hookClass(Class<?> hooker) throws AlbatrossErr
```
- **Critical Errors**:
  - `RequiredErr` - Required element missing
  - `HookerStructErr` - Structural violation
- **Non-critical Errors**:
  - `FieldException`/`MethodException` - Will be caught and logged, hooking continues

### `backupAndHook(Member target, Method hook, Method backup)`
```java
public static boolean backupAndHook(Member target, Method hook, Method backup) throws AlbatrossException
```
- **Possible Exceptions**:
  - `MethodException` - Method signature mismatch
  - `VirtualCallBackupErr` - Backup method visibility error

---

## API Error Mapping

| API Method             | Possible Exceptions                        |
|------------------------|--------------------------------------------|
| `hookClass`           | AlbatrossErr                    |
| `backupAndHook`       | AlbatrossException                |
| `backupField`         | FieldException, AlbatrossErr               |
| `backup`              | AlbatrossException                |
| `hookObject`              | AlbatrossErr                |

## Exception Handling Patterns

### 1. Required Element Handling
```java
try {
    Albatross.hookClass(Hooker.class);
} catch (RequiredErr e) {
    // Critical failure due to required element missing
    Log.e("Albatross", "Required element missing: " + e.getMessage());
}
```

### 2. Structural Validation
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

## Valid Hooker Example

```java
@TargetClass(String.class)
public class StringHooker {
    @FieldRef(required = true)
    static int count; // Required field must exist

    @MethodHook(name = "substring", required = false)
    private static String substringHook(String str, int start, int end) {
        // Hook implementation
       
    }
    
}
```


## Common Error Patterns

| Error Type           | Cause                                   | Fix                                                                     |
|----------------------|-----------------------------------------|-------------------------------------------------------------------------|
| RedundantFieldErr    | Unannotated instance field              | Remove or mark as static                                                |
| Redundant Method     | Unannotated instance method             | Remove or add annotation                                                |
| VirtualCallBackupErr | Non-private backup method               | Add private modifier                                                    |
| NotNativeBackupErr   | Non-native backup method                | Add native modifier                                                     |
| Parameter Mismatch   | Signature mismatch with target method   | Match target method signature or use @ParamInfo or @SubType annotations |
| Return Type Mismatch | Return type doesn't match target method | Adjust return type declaration                                          |
| RequiredFieldErr     | Required field not found                | Remove missing field or set required=false                              |
| RequiredMethodErr    | Required method not found               | Remove missing method or set required=false                             |
| RequiredClassErr     | Required class not found                | Remove missing class or set required=false                              |
| RequiredInstanceErr  | Required instance is null               | Passing the instance object of the target class when call `hookObject`      |
---

## Key Design Principles

1. **Required Elements**:
  - Only fail when `required=true`
  - Non-required element failures are logged but not fatal

2. **Hooker Structure**:
  - Must mirror target class structure exactly
  - No extra instance fields/methods allowed
  - Backup methods must be non-virtual and native

3. **Error Recovery**:
  - Structural errors are fatal
  - Signature mismatches are acceptable
  - Field/method resolution errors are conditionally fatal based on `required` flag





