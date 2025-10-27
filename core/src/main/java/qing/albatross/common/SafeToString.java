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
package qing.albatross.common;

import android.util.ArraySet;

import java.util.Set;

public class SafeToString {

  private static Set<Class<?>> safeToStringClass;


  public static void addSafeToStringClass(Class<?> c) {
    if (safeToStringClass == null)
      safeToStringClass = new ArraySet<>();
    safeToStringClass.add(c);
  }


  /**
   * 将数组转换为字符串，安全处理元素toString()可能抛出的异常
   *
   * @param array 要转换的数组
   * @return 数组的字符串表示
   */
  public static String arrayToString(Object[] array) {
    if (array == null) {
      return "null";
    }
    if (array.length == 0) {
      return "[]";
    }
    int maxIndex = array.length - 1;
    StringBuilder sb = new StringBuilder();
    sb.append('[');
    for (int i = 0; ; i++) {
      // 安全地获取元素的字符串表示
      sb.append(safeToString(array[i]));
      if (i == maxIndex) {
        return sb.append(']').toString();
      }
      sb.append(", ");
    }
  }

  /**
   * 安全地获取对象的字符串表示
   * 如果调用toString()抛出异常，则返回"类名@hashCode"形式
   *
   * @param obj 要转换的对象
   * @return 对象的字符串表示
   */
  public static String safeToString(Object obj) {
    if (obj == null) {
      return "null";
    }
    Class<?> aClass = obj.getClass();
    if (aClass.isPrimitive() || obj instanceof Number)
      return obj.toString();
    if (aClass.isArray()) {
      return arrayToString((Object[]) obj);
    }
    String name = obj.getClass().getName();
    try {
      if (name.startsWith("java.lang")) {
        return obj.toString();
      }
      if (obj instanceof CharSequence) {
        if (name.startsWith("android.") || name.startsWith("java.")) {
          return obj.toString();
        }
      }
      if (safeToStringClass != null && safeToStringClass.contains(aClass)) {
        try {
          return obj.toString();
        } catch (Exception e) {
          safeToStringClass.remove(aClass);
        }
      }
    } catch (Exception ignore) {
    }
    return name + "@" + Integer.toHexString(System.identityHashCode(obj));
  }

}
