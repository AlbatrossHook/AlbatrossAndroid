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
package qing.albatross.nativehook;


import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

import qing.albatross.annotation.Word64;
import qing.albatross.core.Albatross;

public class NativeMethodParser {

  public static final byte ARG_INT = 0;
  public static final byte ARG_BOOL = 1;
  public static final byte ARG_STR = 2;
  public static final byte ARG_BYTE = 3;
  public static final byte ARG_LONG = 4;
  public static final byte ARG_VOID = 5;
  public static final byte ARG_JSON = 6;
  public static final byte ARG_SHORT = 7;
  public static final byte ARG_FLOAT = 8;
  public static final byte ARG_DOUBLE = 9;
  public static final byte ARG_CHAR = 10;
  public static final byte ARG_BYTES = 11;

  public static final byte ARG_U64_WORD = 12;

  private final static Map<Class<?>, Byte> argsMap = new HashMap<>();

  public static final boolean is64;

  static {
    argsMap.put(int.class, ARG_INT);
    argsMap.put(byte.class, ARG_BYTE);
    argsMap.put(boolean.class, ARG_BOOL);
    argsMap.put(long.class, ARG_LONG);
    argsMap.put(String.class, ARG_STR);
    argsMap.put(void.class, ARG_VOID);
    argsMap.put(short.class, ARG_SHORT);
    argsMap.put(float.class, ARG_FLOAT);
    argsMap.put(double.class, ARG_DOUBLE);
    argsMap.put(char.class, ARG_CHAR);
    argsMap.put(byte[].class, ARG_BYTES);
    is64 = (Albatross.getRuntimeISA() & 1) == 0;
  }

  public static NativeMethodRecord parseMethod(Method method) {
    Class<?>[] parameters = method.getParameterTypes();
    byte[] args;
    Annotation[][] parameterAnnotations = method.getParameterAnnotations();
    if (parameters.length > 0) {
      args = new byte[parameters.length];
      for (int i = 0; i < parameters.length; i++) {
        Class<?> parameter = parameters[i];
        if (!is64 && parameter == long.class) {
          boolean isWord = true;
          if (parameterAnnotations[i] != null) {
            for (Annotation annotation : parameterAnnotations[i]) {
              if (annotation instanceof Word64) {
                isWord = false;
                break;
              }
            }
          }
          if (isWord) {
            args[i] = ARG_LONG;
          } else {
            args[i] = ARG_U64_WORD;
          }
          continue;
        }
        Byte aByte = argsMap.get(parameter);
        if (aByte == null)
          return null;
        args[i] = aByte;
      }
    } else {
      args = null;
    }
    Class<?> ret = method.getReturnType();
    Byte retType = argsMap.get(ret);
    if (retType == null)
      return null;
    if (!is64 && ret == long.class) {
      Word64 word = method.getAnnotation(Word64.class);
      if (word != null) {
        retType = ARG_U64_WORD;
      }
    }
    return new NativeMethodRecord(args, retType);
  }

}
