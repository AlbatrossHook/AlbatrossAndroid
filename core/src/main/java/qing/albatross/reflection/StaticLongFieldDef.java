
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

public class StaticLongFieldDef extends FieldDefBase {

  public StaticLongFieldDef(Field field) {
    super(field);
  }

  @Override
  public Class<?> getExpectType() {
    return boolean.class;
  }

  public long get() {
    try {
      return this.field.getLong(null);
    } catch (Exception e) {
      return 0;
    }
  }

  public void set(long value) {
    try {
      this.field.setLong(null, value);
    } catch (Exception e) {
    }
  }

  @Override
  public boolean isStatic() {
    return true;
  }
}
