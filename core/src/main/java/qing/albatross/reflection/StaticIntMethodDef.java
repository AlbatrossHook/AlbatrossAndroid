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


public class StaticIntMethodDef extends MethodDefBase {

  public StaticIntMethodDef(Object target) {
    super(target);
  }

  public StaticIntMethodDef(Set<Class<?>> dependencies, Class<?> cls, Field field) throws Exception {
    super(dependencies, cls, field);
  }

  public boolean isStatic() {
    assert Modifier.isStatic(this.method.getModifiers());
    return true;
  }

  @Override
  public Class<?> getExpectType() {
    return int.class;
  }

  public int invoke(Object... args) {
    try {
      return (int) this.method.invoke(null, args);
    } catch (Exception e) {
      e.printStackTrace();
    }
    return 0;
  }

  public int invokeWithException(Object... args) throws Exception {
    return (int) this.method.invoke(null, args);
  }
}
