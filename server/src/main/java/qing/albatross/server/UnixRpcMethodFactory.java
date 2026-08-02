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
package qing.albatross.server;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;


public class UnixRpcMethodFactory {


  static class RpcMethod {
    Method method;
    byte[] args;
    byte ret;

    public RpcMethod(Method method, byte[] args) {
      super();
      this.method = method;
      this.args = args;
    }

    public static String camelCaseToSnakeCase(String camelCase) {
      if (camelCase == null || camelCase.isEmpty()) {
        return camelCase;
      }
      StringBuilder snakeCase = new StringBuilder();
      // 先处理第一个字符（统一转小写）
      snakeCase.append(Character.toLowerCase(camelCase.charAt(0)));
      // 从第二个字符开始遍历
      for (int i = 1; i < camelCase.length(); i++) {
        char current = camelCase.charAt(i);
        char prev = camelCase.charAt(i - 1);
        // 规则：当前是大写 **且** 前一个是小写 → 才加下划线
        if (Character.isUpperCase(current) && Character.isLowerCase(prev)) {
          snakeCase.append('_');
        }
        // 统一转小写追加
        snakeCase.append(Character.toLowerCase(current));
      }

      return snakeCase.toString();
    }



    public String getName() {
      return camelCaseToSnakeCase(method.getName());
    }
  }

  private final static Map<Class<?>, Byte> argsMap = new HashMap<>();

  static final byte ARG_INT = 0;
  static final byte ARG_BOOL = 1;
  static final byte ARG_STR = 2;
  static final byte ARG_BYTE = 3;
  static final byte ARG_LONG = 4;
  static final byte ARG_VOID = 5;
  static final byte ARG_JSON = 6;
  static final byte ARG_SHORT = 7;
  static final byte ARG_FLOAT = 8;
  static final byte ARG_DOUBLE = 9;
  static final byte ARG_CHAR = 10;
  static final byte ARG_BYTES = 11;


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
  }

  public static Map<String, RpcMethod> parse(Class<?> cls) {
    Map<String, RpcMethod> methodInfoMap = new HashMap<>();
    Method[] methods = cls.getDeclaredMethods();
    for (Method method : methods) {
      String name = method.getName();
      Class<?>[] parameters = method.getParameterTypes();
      RpcMethod rpcMethod;
      if (parameters.length > 0) {
        byte[] args = new byte[parameters.length];
        for (int i = 0; i < parameters.length; i++) {
          args[i] = argsMap.get(parameters[i]);
        }
        rpcMethod = new RpcMethod(method, args);
      } else {
        rpcMethod = new RpcMethod(method, null);
      }
      Class<?> ret = method.getReturnType();
      if (argsMap.containsKey(ret))
        rpcMethod.ret = argsMap.get(ret);
      else
        rpcMethod.ret = ARG_JSON;
      methodInfoMap.put(name, rpcMethod);
    }
    return methodInfoMap;
  }

  public static Map<String, RpcMethod> generateRpcMethods(Class<?> cls) {
    return parse(cls);
  }
}
