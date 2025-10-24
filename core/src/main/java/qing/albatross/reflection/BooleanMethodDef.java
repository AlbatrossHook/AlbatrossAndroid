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
import java.lang.reflect.Modifier;
import java.util.Set;


public class BooleanMethodDef extends MethodDefBase {

  public BooleanMethodDef(Object target) {
    super(target);
  }

  public BooleanMethodDef(Set<Class<?>> dependencies, Class<?> cls, Field field) throws Exception {
    super(dependencies, cls, field);
  }

  public boolean isStatic() {
    assert !Modifier.isStatic(this.method.getModifiers());
    return false;
  }

  @Override
  public Class<?> getExpectType() {
    return boolean.class;
  }

  public boolean invoke(Object receiver, Object... args) {
    try {
      return (boolean) this.method.invoke(receiver, args);
    } catch (Exception e) {
      e.printStackTrace();
    }
    return false;
  }

  public boolean invokeWithException(Object receiver, Object... args) throws Exception {
    return (boolean) this.method.invoke(receiver, args);
  }
}
