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

import java.lang.reflect.Constructor;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.util.Set;

import qing.albatross.core.Albatross;

public class SafeToString {

  private static Set<Class<?>> safeToStringClass;

  // 配置参数（可按需调整）
  private static final int MAX_TOTAL_LENGTH = 4096;      // 总字符数上限
  private static final int MAX_ARRAY_ELEMENTS = 35;      // 数组最多显示几个元素
  private static final String TRUNCATED_SUFFIX = "..."; // 裁剪提示
  private static final String TRUNCATED_SUFFIX_LIST = " ...]";

  public static void addSafeToStringClass(Class<?> c) {
    if (safeToStringClass == null)
      safeToStringClass = new ArraySet<>();
    safeToStringClass.add(c);
  }

  public static boolean isSafeToString(Class<?> c) {
    if (CharSequence.class.isAssignableFrom(c)) {
      String name = c.getName();
      if (name.startsWith("android.") || name.startsWith("java.")) {
        return true;
      }
    }
    if (safeToStringClass == null)
      return false;
    return safeToStringClass.contains(c);
  }

  /**
   * 安全地将对象转换为字符串，并限制长度
   */
  public static String safeToString(Object obj) {
    return safeToString(new StringBuilder(), obj, MAX_TOTAL_LENGTH, MAX_ARRAY_ELEMENTS).toString();
  }

  public static StringBuilder safeToString(StringBuilder builder, Object obj, int maxLength, int maxArrayElements) {
    if (obj == null) {
      return builder.append("null");
    }
    Class<?> aClass = obj.getClass();
    // 基础类型或 Number 直接返回
    if (aClass.isPrimitive() || obj instanceof Number || obj instanceof Boolean) {
      return builder.append(obj.toString());
    }
    // 处理数组
    if (aClass.isArray()) {
      if (aClass.getComponentType().isPrimitive())
        return primitiveArrayToString(builder, obj, maxLength, maxArrayElements);
      else
        return arrayToString(builder, (Object[]) obj, maxLength, maxArrayElements);
    }
    String name = aClass.getName();
    try {
      // Java 标准库对象直接 toString()
      if (name.startsWith("java.lang")) {
        if (obj instanceof Method || obj instanceof Constructor<?>) {
          return builder.append(Albatross.methodToString((Member) obj));
        }
        return builder.append(safeTruncate(obj.toString(), maxLength));
      }
      // CharSequence 类型（String、StringBuilder 等）
      if (obj instanceof CharSequence) {
        if (name.startsWith("android.") || name.startsWith("java.")) {
          return builder.append(safeTruncate(obj.toString(), maxLength));
        }
      }
      // 用户注册的安全类
      if (safeToStringClass != null && safeToStringClass.contains(aClass)) {
        try {
          return builder.append(safeTruncate(obj.toString(), maxLength));
        } catch (Exception e) {
          safeToStringClass.remove(aClass); // 失败后移除
        }
      }
    } catch (Exception ignore) {
    }
    // 默认 fallback：类名 + hashCode
    return builder.append(name).append("@").append(Integer.toHexString(System.identityHashCode(obj)));
//    return safeTruncate(fallback, maxLength);
  }

  /**
   * 安全截断字符串，确保不超过 maxLength
   */
  private static String safeTruncate(String str, int maxLength) {
    if (str == null || str.length() <= maxLength) {
      return str;
    }
    if (maxLength <= TRUNCATED_SUFFIX.length()) {
      return TRUNCATED_SUFFIX.substring(0, maxLength);
    }
    return str.substring(0, maxLength - TRUNCATED_SUFFIX.length()) + TRUNCATED_SUFFIX;
  }

  public static String arrayToString(Object[] array) {
    return arrayToString(new StringBuilder(), array, MAX_TOTAL_LENGTH, MAX_ARRAY_ELEMENTS).toString();
  }

  /**
   * 将对象数组转换为字符串，带长度和元素数量限制
   */
  public static StringBuilder arrayToString(StringBuilder builder, Object[] array, int maxTotalLength, int maxArrayElements) {
    if (array == null) {
      return builder.append("null");
    }
    if (array.length == 0) {
      return builder.append("[]");
    }
    int elementsToShow = Math.min(array.length, maxArrayElements);
    boolean arrayTruncated = elementsToShow < array.length;
    if (arrayTruncated)
      maxTotalLength -= 5;
    if (array.length > 20) {
      String lenInfo = "[=" + array.length + ", ";
      builder.append(lenInfo);
      maxTotalLength -= lenInfo.length();
    } else {
      builder.append('[');
      maxTotalLength -= 1;
    }
    for (int i = 0; i < elementsToShow; i++) {
      // 计算剩余可用长度
      if (maxTotalLength <= 0) {
        // 剩余空间不足，直接截断
        return builder.append(TRUNCATED_SUFFIX_LIST);
      }
      // 递归处理数组元素，限制其长度
      Object obj = array[i];
      if (obj == null) {
        builder.append(",");
        maxTotalLength -= 1;
        continue;
      }
      int len = builder.length();
      safeToString(builder, obj, maxTotalLength, maxArrayElements);
      int objLen = builder.length() - len;
      maxTotalLength -= objLen;
      if (i != elementsToShow - 1) {
        builder.append(", ");
        maxTotalLength -= 2;
      }
    }
    // 添加数组截断标记
    if (arrayTruncated) {
      return builder.append(TRUNCATED_SUFFIX_LIST);
    } else {
      return builder.append(']');
    }
  }

  /**
   * 将任意数组（包括原始类型）转为字符串，并限制长度和元素数量
   */
  public static StringBuilder primitiveArrayToString(StringBuilder builder, Object array, int maxLength, int maxArrayElements) {
    if (array == null) return builder.append("null");
    int length = java.lang.reflect.Array.getLength(array);
    if (length == 0) return builder.append("[]");
//    // 如果只允许极短长度，直接返回简略形式
//    StringBuilder sb = new StringBuilder();
    if (length > 20) {
      if (maxLength <= 10) {
        return builder.append("[total=").append(length).append("]");
      }
      String lenInfo = "[len=" + length + ",";
      builder.append(lenInfo);
      maxLength -= lenInfo.length();
    } else {
      if (maxLength <= 5) {
        return builder.append("[=").append(length).append("]");
      }
      builder.append('[');
      maxLength -= 1;
    }
    int elementsToShow = Math.min(length, maxArrayElements);
    if (length != elementsToShow) {
      maxLength -= 5;
    }
    for (int i = 0; i < elementsToShow; i++) {
      Object element = java.lang.reflect.Array.get(array, i);
      String elemStr = element.toString(); // 预留空间给 ", ...]"
      if (i > 0) {
        builder.append(",");
        maxLength -= 1;
      }
      builder.append(elemStr);
      maxLength -= elemStr.length();
      // 提前检查是否超限
      if (maxLength <= 0 && i < elementsToShow - 1) { // 预留空间给裁剪提示
        return builder.append(TRUNCATED_SUFFIX_LIST);
      }
    }
    if (elementsToShow < length) {
      return builder.append(TRUNCATED_SUFFIX_LIST);
    } else {
      return builder.append(']');
    }
  }
}