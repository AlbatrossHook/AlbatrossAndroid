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
package qing.albatross.core;

import static qing.albatross.core.InstructionListener.NumberOfVRegs;

import java.lang.reflect.Constructor;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import qing.albatross.common.SafeToString;

public class MethodInvokeFrame {
  public Member member;
  protected int numberVRegs = -1;
  protected int firstArgReg = -1;
  Class<?>[] parameterTypes;
  static final int FLAG_GET_SLOW = 1;
  static final int FLAG_STRING_ARGS = 2;
  static final int FLAG_STRING_CONVERT = 4;
  private int frameFlags;

  static final Class<?>[] emptyParameterTypes = new Class[0];
  static final Object[] emptyArguments = new Object[0];

  public void setMember(Member member) {
    if (member != this.member) {
      this.member = member;
      firstArgReg = -1;
      numberVRegs = -1;
      frameFlags = 0;
    }
  }

  public  boolean unHook(){
    return false;
  }

  void createParameterTypeList(int count) {
    if (parameterTypes != null && parameterTypes.length == count)
      return;
    if (count == 0) {
      parameterTypes = emptyParameterTypes;
    } else
      parameterTypes = new Class[count];
  }


  public int getFirstArgReg(long invocationContext) {
    if (firstArgReg >= 0)
      return firstArgReg;
    numberVRegs = NumberOfVRegs(invocationContext);
    Class<?>[] paramTypes;
    int argUsedVregCount = 0;
    if (member instanceof Method method) {
      paramTypes = method.getParameterTypes();
      if (!Modifier.isStatic(method.getModifiers())) {
        createParameterTypeList(paramTypes.length + 1);
        Class<?> declaringClass = method.getDeclaringClass();
        parameterTypes[0] = declaringClass;
        argUsedVregCount += 1;
        if (SafeToString.isSafeToString(declaringClass))
          frameFlags |= FLAG_STRING_CONVERT;
      } else
        createParameterTypeList(paramTypes.length);
    } else {
      Constructor<?> constructor = (Constructor<?>) member;
      paramTypes = constructor.getParameterTypes();
      if (!Modifier.isStatic(constructor.getModifiers())) {
        createParameterTypeList(paramTypes.length + 1);
        Class<?> declaringClass = constructor.getDeclaringClass();
        if (SafeToString.isSafeToString(declaringClass))
          frameFlags |= FLAG_STRING_CONVERT;
        parameterTypes[0] = declaringClass;
        argUsedVregCount = 1;
      } else
        createParameterTypeList(paramTypes.length);
    }
    int slotIdx = argUsedVregCount;
    for (Class<?> c : paramTypes) {
      argUsedVregCount += 1;
      if (c == String.class || c == StringBuilder.class || c == StringBuffer.class) {
        frameFlags |= FLAG_STRING_ARGS | FLAG_STRING_CONVERT;
      } else if (c.isPrimitive() || SafeToString.isSafeToString(c)) {
        frameFlags |= FLAG_STRING_CONVERT;
        if (c == long.class || c == double.class) {
          frameFlags |= FLAG_GET_SLOW;
          argUsedVregCount += 1;
        }
      }
      parameterTypes[slotIdx] = c;
      slotIdx++;
    }
    firstArgReg = numberVRegs - argUsedVregCount;
    return firstArgReg;
  }

  public Class<?>[] getParameterTypes(long invocationContext) {
    if (firstArgReg >= 0)
      return parameterTypes;
    if (invocationContext == 0)
      return null;
    getFirstArgReg(invocationContext);
    return parameterTypes;
  }

  public int getNumberVRegs(long invocationContext) {
    if (numberVRegs >= 0)
      return numberVRegs;
    numberVRegs = NumberOfVRegs(invocationContext);
    return numberVRegs;
  }


  public int getArgReg(long invocationContext, int i) {
    int idx = getFirstArgReg(invocationContext);
    if ((frameFlags & FLAG_GET_SLOW) == 0)
      idx = +i;
    else {
      for (int z = 0; z < i; z++) {
        Class<?> argType = parameterTypes[z];
        if (argType == double.class || argType == long.class) {
          idx += 2;
        } else
          idx += 1;
      }
    }
    assert idx < numberVRegs;
    return idx;
  }

  public void setParamObject(long invocationContext, int i, Object o) {
    int idx = getFirstArgReg(invocationContext);
    assert o == null || parameterTypes[i].isAssignableFrom(o.getClass());
    if ((frameFlags & FLAG_GET_SLOW) == 0)
      idx += i;
    else {
      for (int z = 0; z < i; z++) {
        Class<?> argType = parameterTypes[z];
        if (argType == double.class || argType == long.class) {
          idx += 2;
        } else
          idx += 1;
      }
    }
    assert idx < numberVRegs;
    InstructionListener.SetVRegReference(invocationContext, idx, o);
  }

  public <T> int setParamPrim(long invocationContext, int i, int v, Class<T> clz) {
    int idx = getFirstArgReg(invocationContext);
    assert clz == parameterTypes[i];
    if ((frameFlags & FLAG_GET_SLOW) == 0)
      idx += i;
    else {
      for (int z = 0; z < i; z++) {
        Class<?> argType = parameterTypes[z];
        if (argType == double.class || argType == long.class) {
          idx += 2;
        } else
          idx += 1;
      }
    }
    assert idx < numberVRegs;
    return InstructionListener.SetVReg(invocationContext, idx, v);
  }

  public double setParamDouble(long invocationContext, int i, double v) {
    assert parameterTypes[i] == double.class;
    int idx = getArgReg(invocationContext, i);
    return InstructionListener.SetVRegDouble(invocationContext, idx, v);
  }

  public long setParamLong(long invocationContext, int i, long v) {
    assert parameterTypes[i] == long.class;
    int idx = getArgReg(invocationContext, i);
    return InstructionListener.SetVRegLong(invocationContext, idx, v);
  }

  public float setParamFloat(long invocationContext, int i, float v) {
    assert parameterTypes[i] == float.class;
    int idx = getArgReg(invocationContext, i);
    return InstructionListener.SetVRegFloat(invocationContext, idx, v);
  }

  public <T> T getParamObject(long invocationContext, int i, Class<T> clz) {
    int idx = getFirstArgReg(invocationContext);
    assert parameterTypes[i].isAssignableFrom(clz);
    if ((frameFlags & FLAG_GET_SLOW) == 0)
      idx += i;
    else {
      for (int z = 0; z < i; z++) {
        Class<?> argType = parameterTypes[z];
        if (argType == double.class || argType == long.class) {
          idx += 2;
        } else
          idx += 1;
      }
    }
    assert idx < numberVRegs;
    return (T) InstructionListener.GetVRegReference(invocationContext, idx);
  }

  public int getParamPrim(long invocationContext, int i, Class<?> clz) {
    int idx = getFirstArgReg(invocationContext);
    assert clz == parameterTypes[i];
    if ((frameFlags & FLAG_GET_SLOW) == 0)
      idx += i;
    else {
      for (int z = 0; z < i; z++) {
        Class<?> argType = parameterTypes[z];
        if (argType == double.class || argType == long.class) {
          idx += 2;
        } else
          idx += 1;
      }
    }
    assert idx < numberVRegs;
    return InstructionListener.GetVReg(invocationContext, idx);
  }

  public double getParamDouble(long invocationContext, int i) {
    assert parameterTypes[i] == double.class;
    int idx = getArgReg(invocationContext, i);
    return InstructionListener.GetVRegDouble(invocationContext, idx);
  }

  public long getParamLong(long invocationContext, int i) {
    assert parameterTypes[i] == long.class;
    int idx = getArgReg(invocationContext, i);
    return InstructionListener.GetVRegLong(invocationContext, idx);
  }

  public float getParamFloat(long invocationContext, int i) {
    assert parameterTypes[i] == float.class;
    int idx = getArgReg(invocationContext, i);
    return InstructionListener.GetVRegFloat(invocationContext, idx);
  }


  public int getArgRegTwoWord(long invocationContext, int i) {
    int idx = getFirstArgReg(invocationContext) + i;
    assert idx + 1 < numberVRegs;
    return idx;
  }

  public Object[] getArguments(long invocationContext) {
    if (invocationContext == 0)
      return null;
    try {
      int first = getFirstArgReg(invocationContext);
      if (parameterTypes.length == 0) {
        return emptyArguments;
      }
      int i = first;
      Class<?>[] argTypes = parameterTypes;
      int argCount = parameterTypes.length;
      int argIdx = 0;
      Object[] arguments = new Object[argCount];
      for (; i < numberVRegs; i++) {
        Class<?> t = argTypes[argIdx];
        if (t.isPrimitive()) {
          Object valueObject;
          if (t == int.class) {
            valueObject = InstructionListener.GetVReg(invocationContext, i);
          } else if (t == boolean.class) {
            valueObject = InstructionListener.GetVReg(invocationContext, i) != 0;
          } else if (t == char.class) {
            valueObject = (char) InstructionListener.GetVReg(invocationContext, i);
          } else if (t == long.class) {
            valueObject = InstructionListener.GetVRegLong(invocationContext, i);
            i += 1;
          } else if (t == void.class || t == Void.class) {
            valueObject = null;
          } else if (t == float.class) {
            valueObject = InstructionListener.GetVRegFloat(invocationContext, i);
          } else if (t == double.class) {
            valueObject = InstructionListener.GetVRegDouble(invocationContext, i);
            i += 1;
          } else if (t == byte.class) {
            valueObject = (byte) InstructionListener.GetVRegLong(invocationContext, i);
          } else {
            valueObject = (short) InstructionListener.GetVRegLong(invocationContext, i);
          }
          arguments[argIdx] = valueObject;
        } else
          arguments[argIdx] = InstructionListener.GetVRegReference(invocationContext, i);
        argIdx++;
      }
      return arguments;
    } catch (Throwable e) {
      Albatross.log("getArguments err", e);
      return null;
    }
  }

  public Object[] getToStringArguments(long invocationContext) {
    if (invocationContext == 0)
      return null;
    try {
      int i = getFirstArgReg(invocationContext);
      if ((frameFlags & FLAG_STRING_CONVERT) == 0)
        return emptyArguments;
      Class<?>[] argTypes = parameterTypes;
      int argCount = parameterTypes.length;
      Object[] arguments = new Object[argCount];
      int argIdx = 0;
      for (; i < numberVRegs; i++) {
        Class<?> t = argTypes[argIdx];
        if (t.isPrimitive()) {
          if (t == long.class) {
            long valueObject = InstructionListener.GetVRegLong(invocationContext, i);
            arguments[argIdx] = valueObject;
            i += 1;
          } else if (t == double.class) {
            double valueObject = InstructionListener.GetVRegDouble(invocationContext, i);
            arguments[argIdx] = valueObject;
            i += 1;
          } else {
            int valueObject = InstructionListener.GetVReg(invocationContext, i);
            arguments[argIdx] = valueObject;
          }
        } else if (t == String.class || t == StringBuilder.class) {
          Object o = InstructionListener.GetVRegReference(invocationContext, i);
          if (o != null) {
            arguments[argIdx] = o;
          }
        } else if (SafeToString.isSafeToString(t)) {
          Object o = InstructionListener.GetVRegReference(invocationContext, i);
          if (o != null) {
            arguments[argIdx] = o.toString();
          }
        }
        argIdx++;
      }
      return arguments;
    } catch (Throwable e) {
      Albatross.log("getArguments err", e);
      return null;
    }
  }

  public Object[] getStringArguments(long invocationContext) {
    if (invocationContext == 0)
      return null;
    try {
      int i = getFirstArgReg(invocationContext);
      if ((frameFlags & FLAG_STRING_ARGS) == 0)
        return emptyArguments;
      Class<?>[] argTypes = parameterTypes;
      int argCount = parameterTypes.length;
      Object[] arguments = new Object[argCount];
      int argIdx = 0;
      for (; i < numberVRegs; i++) {
        Class<?> t = argTypes[argIdx];
        if (t.isPrimitive()) {
          if (t == long.class) {
            i += 1;
          } else if (t == double.class) {
            i += 1;
          }
        } else if (t == String.class || t == StringBuilder.class || t == StringBuffer.class) {
          Object o = InstructionListener.GetVRegReference(invocationContext, i);
          if (o != null) {
            arguments[argIdx] = o.toString();
          }
        } else if (SafeToString.isSafeToString(t)) {
          Object o = InstructionListener.GetVRegReference(invocationContext, i);
          if (o != null) {
            arguments[argIdx] = o.toString();
          }
        }
        argIdx++;
      }
      return arguments;
    } catch (Throwable e) {
      Albatross.log("getArguments err", e);
      return null;
    }
  }


}
