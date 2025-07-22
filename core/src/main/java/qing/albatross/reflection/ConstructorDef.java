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
import java.util.Set;

import qing.albatross.annotation.ArgumentTypeName;
import qing.albatross.annotation.ArgumentType;
import qing.albatross.core.Albatross;

public class ConstructorDef<T> extends ReflectionBase {
  private final Constructor<T> constructor;

  public ConstructorDef(Object target) {
    this.constructor = (Constructor<T>) target;
    constructor.setAccessible(true);
  }

  public Constructor<T> getConstructor() {
    return constructor;
  }

  public ConstructorDef(Set<Class<?>> dependencies, Class<?> cls, Field field) throws Exception {
    ArgumentType argumentType = field.getAnnotation(ArgumentType.class);
    ArgumentTypeName argumentTypeName;
    ClassLoader classLoader = cls.getClassLoader();
    if (argumentType != null) {
      Class<?>[] parameterTypes = argumentType.value();
      Albatross.checkParameterTypes(dependencies, parameterTypes, null, classLoader);
      this.constructor = (Constructor<T>) cls.getDeclaredConstructor(parameterTypes);
    } else if ((argumentTypeName = field.getAnnotation(ArgumentTypeName.class)) != null) {
      String[] classes = argumentTypeName.value();
      Class<?>[] paramClasses = ReflectUtils.getArgumentTypesFromString(classes, classLoader, false);
      this.constructor = (Constructor<T>) cls.getDeclaredConstructor(paramClasses);
    } else {
      this.constructor = (Constructor<T>) cls.getDeclaredConstructor();
    }
    this.constructor.setAccessible(true);
  }

  @Override
  public Class<?> getExpectType() {
    try {
      return getFieldGenericType(getClass().getDeclaredField("constructor"));
    } catch (NoSuchFieldException e) {
      return Object.class;
    }
  }

  @Override
  public boolean isStatic() {
    return false;
  }

  @Override
  public Class<?> getRealType() {
    return this.constructor.getDeclaringClass();
  }

  public T newInstance() {
    try {
      return this.constructor.newInstance();
    } catch (Exception e) {
      return null;
    }
  }

  public T newInstance(Object... args) {
    try {
      return this.constructor.newInstance(args);
    } catch (Exception e) {
      return null;
    }
  }
}
