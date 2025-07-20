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

public class FieldDef<T> extends FieldDefBase {

  GenericClass<T> t;

  public Class<T> getType() {
    return (Class<T>) field.getType();
  }

  public FieldDef(Field field) {
    super(field);
  }

  @Override
  public Class<?> getExpectType() {
    try {
      return getFieldGenericType(getClass().getDeclaredField("t"));
    } catch (NoSuchFieldException e) {
      return Object.class;
    }
  }

  public T get(Object receiver) {
    try {
      return (T) this.field.get(receiver);
    } catch (Exception e) {
      return null;
    }
  }

  public boolean set(Object receiver, T value) {
    try {
      this.field.set(receiver, value);
      return true;
    } catch (Exception e) {
      return false;
    }

  }
}