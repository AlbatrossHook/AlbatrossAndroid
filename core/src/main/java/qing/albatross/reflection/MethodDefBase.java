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

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Set;

import qing.albatross.annotation.ArgumentType;
import qing.albatross.annotation.ArgumentTypeName;
import qing.albatross.annotation.ByName;
import qing.albatross.core.Albatross;

class MethodDefBase extends ReflectionBase {
  public final Method method;

  public MethodDefBase(Object target) {
    this.method = (Method) target;
    method.setAccessible(true);
  }

  public MethodDefBase(Set<Class<?>> dependencies, Class<?> cls, Field field) throws Exception {
    String fieldName = field.getName();
    ArgumentType argumentType = field.getAnnotation(ArgumentType.class);
    ArgumentTypeName typeName;
    ClassLoader classLoader = cls.getClassLoader();
    if (argumentType != null) {
      Class<?>[] parameterTypes = argumentType.value();
      Albatross.checkParameterTypes(dependencies, parameterTypes, null, classLoader);
      if (argumentType.exactSearch())
        this.method = ReflectUtils.findMethod(cls, fieldName, parameterTypes);
      else
        this.method = ReflectUtils.findDeclaredMethodWithSubArgType(cls, fieldName, parameterTypes);
    } else if ((typeName = field.getAnnotation(ArgumentTypeName.class)) != null) {
      String[] classes = typeName.value();
      Class<?>[] paramClasses = ReflectUtils.getArgumentTypesFromString(classes, classLoader, false);
      this.method = ReflectUtils.findMethod(cls, fieldName, paramClasses);
    } else if (field.getAnnotation(ByName.class) != null) {
      this.method = ReflectUtils.findDeclaredMethodByName(cls, fieldName);
    } else {
      this.method = ReflectUtils.findMethod(cls, fieldName);
    }
    this.method.setAccessible(true);
  }

  public boolean isStatic() {
    return Modifier.isStatic(this.method.getModifiers());
  }

  @Override
  public Class<?> getRealType() {
    return this.method.getReturnType();
  }


}
