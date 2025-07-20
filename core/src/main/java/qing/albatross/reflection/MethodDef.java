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
import qing.albatross.core.Albatross;


public class MethodDef<T> extends MethodDefBase {

  public MethodDef(Object target) {
    super(target);
  }

  public MethodDef(Set<Class<?>> dependencies, Class<?> cls, Field field) throws Exception {
    super(dependencies, cls, field);
  }


  public T invoke(Object receiver, Object... args) {
    try {
      return (T) this.method.invoke(receiver, args);
    } catch (Exception e) {
      e.printStackTrace();
      return null;
    }
  }

  public T invokeWithException(Object receiver, Object... args) throws Exception {
    return (T) this.method.invoke(receiver, args);
  }
}
