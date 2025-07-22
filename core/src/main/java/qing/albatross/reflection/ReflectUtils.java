/*
 * Copyright 2025 QingWan (qingwanmail@foxmail.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */


package qing.albatross.reflection;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;

import qing.albatross.core.Albatross;
import qing.albatross.exception.AlbatrossErr;
import qing.albatross.exception.CheckParameterTypesResult;

public class ReflectUtils {

  public static Method findMethod(Class<?> clazz, String name, Class<?>... parameterTypes) throws NoSuchMethodException {
    while (clazz != null) {
      try {
        Method method = clazz.getDeclaredMethod(name, parameterTypes);
        if (!method.isAccessible()) {
          method.setAccessible(true);
        }
        return method;
      } catch (NoSuchMethodException e) {
        clazz = clazz.getSuperclass();
        Albatross.addToVisit(clazz);
      }
    }
    throw new NoSuchMethodException("Method " + name + " with parameters " + Arrays.asList(parameterTypes) + " not found in " + clazz);
  }

  public static Method findDeclaredMethodWithType(Class<?> clazz, String name, Class<?>[] argTypes, CheckParameterTypesResult checkParameterTypesResult) throws AlbatrossErr {
    Class<?>[] subArgTypes = checkParameterTypesResult.mParameterSubTypes;
    Method[] methods = clazz.getDeclaredMethods();
    int expectParamCount = argTypes.length;
    for (Method method : methods) {
      if (method.getName().equals(name) && method.getParameterCount() == expectParamCount) {
        Class<?>[] parameterTypes = method.getParameterTypes();
        int i = 0;
        for (; i < expectParamCount; i++) {
          int pIdx = i + checkParameterTypesResult.offset;
          Class<?> subClz = subArgTypes[pIdx];
          if (subClz != null) {
            if (!subClz.isAssignableFrom(parameterTypes[i]))
              break;
          } else if (parameterTypes[i] != argTypes[i])
            break;
        }
        if (i == expectParamCount) {
          Class<?>[] hookerClasses = checkParameterTypesResult.hookerClasses;
          if (hookerClasses != null) {
            for (int j = 0; j < hookerClasses.length; j++) {
              Class<?> hooker = hookerClasses[j];
              if (hooker != null) {
                Class<?> targetClass = parameterTypes[j - checkParameterTypesResult.offset];
                Albatross.addAssignableHooker(hooker, targetClass);
              }
            }
          }
          return method;
        }
      }
    }
    return null;
  }

  public static Method findMethodWithType(Class<?> clazz, String name, Class<?>[] argTypes, CheckParameterTypesResult checkParameterTypesResult) throws AlbatrossErr {
    while (clazz != null) {
      Method method = findDeclaredMethodWithType(clazz, name, argTypes, checkParameterTypesResult);
      if (method != null)
        return method;
      clazz = clazz.getSuperclass();
      Albatross.addToVisit(clazz);
    }
    return null;
  }


  public static Method findDeclaredMethodWithSubArgType(Class<?> clazz, String name, Class<?>[] subArgTypes) throws NoSuchMethodException {
    Method[] methods = clazz.getDeclaredMethods();
    int expectParamCount = subArgTypes.length;
    for (Method method : methods) {
      if (method.getName().equals(name) && method.getParameterCount() == expectParamCount) {
        Class<?>[] parameterTypes = method.getParameterTypes();
        int i = 0;
        for (; i < expectParamCount; i++) {
          Class<?> subClz = subArgTypes[i];
          if (subClz != null) {
            if (!subClz.isAssignableFrom(parameterTypes[i]))
              break;
          }
        }
        if (i == expectParamCount)
          return method;

      }
    }
    throw new NoSuchMethodException("Method " + name + " with subType parameters " + Arrays.asList(subArgTypes) + " not found in " + clazz);
  }


  public static Constructor<?> findDeclaredConstructorWithType(Class<?> clazz, Class<?>[] argTypes, CheckParameterTypesResult parameterTypesResult) throws AlbatrossErr {
    Constructor<?>[] methods = clazz.getDeclaredConstructors();
    int expectParamCount = argTypes.length;
    Class<?>[] subArgTypes = parameterTypesResult.mParameterSubTypes;
    for (Constructor<?> method : methods) {
      Class<?>[] parameterTypes = method.getParameterTypes();
      if (parameterTypes.length == expectParamCount) {
        int i = 0;
        for (; i < expectParamCount; i++) {
          Class<?> subClz = subArgTypes[i + parameterTypesResult.offset];
          if (subClz != null) {
            if (!subClz.isAssignableFrom(parameterTypes[i]))
              break;
          } else if (parameterTypes[i] != argTypes[i])
            break;
        }
        if (i == expectParamCount) {
          Class<?>[] hookerClasses = parameterTypesResult.hookerClasses;
          if (hookerClasses != null) {
            for (int j = 0; j < hookerClasses.length; j++) {
              Class<?> hooker = hookerClasses[j];
              if (hooker != null) {
                Class<?> targetClass = parameterTypes[j - parameterTypesResult.offset];
                Albatross.addAssignableHooker(hooker, targetClass);
              }
            }
          }
          return method;
        }
      }
    }
    return null;
  }

  public static Field findField(Class<?> clazz, String name) throws NoSuchFieldException {
    while (clazz != null) {
      try {
        Field field = clazz.getDeclaredField(name);
        if (!field.isAccessible()) {
          field.setAccessible(true);
        }
        return field;
      } catch (NoSuchFieldException e) {
        clazz = clazz.getSuperclass();
        Albatross.addToVisit(clazz);
      }
    }
    throw new NoSuchFieldException("Field " + name + " not found");
  }

  public static Field findField(Class<?> clazz, String[] names) throws NoSuchFieldException {
    if (names.length == 0)
      throw new NoSuchFieldException("Field  not found");
    while (clazz != null) {
      try {
        Field field = findDeclaredField(clazz, names);
        if (!field.isAccessible()) {
          field.setAccessible(true);
        }
        return field;
      } catch (NoSuchFieldException e) {
        clazz = clazz.getSuperclass();
        Albatross.addToVisit(clazz);
      }
    }
    throw new NoSuchFieldException("Field " + names[0] + " not found");
  }

  public static Field findDeclaredField(Class<?> clazz, String[] names) throws NoSuchFieldException {
    if (names.length == 0)
      throw new NoSuchFieldException("Field not found");
    for (String name : names) {
      try {
        Field field = clazz.getDeclaredField(name);
        if (!field.isAccessible()) {
          field.setAccessible(true);
        }
        return field;
      } catch (NoSuchFieldException ignore) {
      }
    }
    throw new NoSuchFieldException("Field " + names[0] + " not found");
  }


  public static boolean isInstanceOf(Class<?> cls, Class<?> clazz) {
    do {
      if (cls == clazz)
        return true;
      cls = cls.getSuperclass();
    } while (cls != null && cls != Object.class);
    return false;
  }

  public static boolean isInterfaceOf(Class<?> cls, Class<?> clazz) {
    do {
      if (cls == clazz)
        return true;
      Class<?>[] interfaces = cls.getInterfaces();
      for (Class<?> i : interfaces) {
        if (i == clazz)
          return true;
      }
      cls = cls.getSuperclass();
    } while (cls != null && cls != Object.class);
    return false;
  }

  public static Class<?>[] getArgumentTypesFromString(String[] stringArgs, ClassLoader loader, boolean initClass) throws ClassNotFoundException {
    Class<?>[] argTypes = new Class[stringArgs.length];
    for (int i = 0; i < stringArgs.length; i++) {
      String argClassName = stringArgs[i];
      switch (argClassName) {
        case "boolean":
          argTypes[i] = boolean.class;
          break;
        case "byte":
          argTypes[i] = byte.class;
          break;
        case "char":
          argTypes[i] = char.class;
          break;
        case "short":
          argTypes[i] = short.class;
          break;
        case "int":
          argTypes[i] = int.class;
          break;
        case "long":
          argTypes[i] = long.class;
          break;
        case "float":
          argTypes[i] = float.class;
          break;
        case "double":
          argTypes[i] = double.class;
          break;
        case "void":
          argTypes[i] = void.class;
        default:
          argTypes[i] = Class.forName(argClassName, initClass, loader);
          break;
      }
    }
    return argTypes;
  }


}
