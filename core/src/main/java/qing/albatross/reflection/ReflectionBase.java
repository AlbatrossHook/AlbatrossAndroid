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
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;

public abstract class ReflectionBase {
  public Class<?> getRealType() {
    return Object.class;
  }

  public Class<?> getExpectType() {
    return Object.class;
  }

  public static Class<?> getGenericType(Type genericType) {
    if (genericType instanceof ParameterizedType) {
      ParameterizedType type = (ParameterizedType) genericType;
      Type[] types = type.getActualTypeArguments();
      if (types.length > 0) {
        Type t = types[0];
        if (t instanceof Class<?>)
          return (Class<?>) t;
        else if (t instanceof ParameterizedType) {
          return (Class<?>) ((ParameterizedType) t).getRawType();
        } else {
          return null;
        }
      }
    }
    return null;
  }
  public abstract boolean isStatic();

  public static Class<?> getFieldGenericType(Field field) {
    Type genericType = field.getGenericType();
    return getGenericType(genericType);
  }

  public static class GenericClass<T> {
    public GenericClass() {
    }
  }

}
