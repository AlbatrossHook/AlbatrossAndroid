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

public class MethodInvokeFrame {
  public Member member;
  protected int numberVRegs = -1;
  protected int firstArgReg = -1;

  public void setMember(Member member) {
    if (member != this.member) {
      this.member = member;
      firstArgReg = -1;
      numberVRegs = -1;
    }
  }

  public int getFirstArgReg(long invocationContext) {
    if (firstArgReg >= 0)
      return firstArgReg;
    numberVRegs = NumberOfVRegs(invocationContext);
    if (member instanceof Method method) {
      int argUsedVregCount = 0;
      Class<?>[] parameterTypes = method.getParameterTypes();
      for (Class<?> c : parameterTypes) {
        if (c == long.class || c == double.class) {
          argUsedVregCount += 2;
        } else
          argUsedVregCount += 1;
      }
      if (!Modifier.isStatic(method.getModifiers()))
        argUsedVregCount += 1;
      firstArgReg = numberVRegs - argUsedVregCount;
    } else {
      Constructor<?> constructor = (Constructor<?>) member;
      Class<?>[] parameterTypes = constructor.getParameterTypes();
      int argUsedVregCount = 1;
      if (Modifier.isStatic(constructor.getModifiers()))
        argUsedVregCount = 0;
      for (Class<?> c : parameterTypes) {
        if (c == long.class || c == double.class) {
          argUsedVregCount += 2;
        } else
          argUsedVregCount += 1;
      }
      firstArgReg = numberVRegs - argUsedVregCount;
    }
    return firstArgReg;
  }

  public int getNumberVRegs(long invocationContext) {
    if (numberVRegs >= 0)
      return numberVRegs;
    numberVRegs = NumberOfVRegs(invocationContext);
    return numberVRegs;
  }

  public int getArgReg(long invocationContext, int i) {
    int idx = getFirstArgReg(invocationContext) + i;
    assert idx < numberVRegs;
    return idx;
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
      Object thiz = null;
      int i = first;
      Class<?>[] argTypes;
      int argCount;
      int argIdx = 0;
      if (member instanceof Method method) {
        argCount = method.getParameterCount();
        if (!Modifier.isStatic(method.getModifiers())) {
          thiz = InstructionListener.GetVRegReference(invocationContext, first);
          argCount += 1;
        }
        argTypes = method.getParameterTypes();
      } else {
        Constructor<?> constructor = (Constructor<?>) member;
        argTypes = constructor.getParameterTypes();
        argCount = argTypes.length;
        if (!Modifier.isStatic(constructor.getModifiers())) {
          thiz = InstructionListener.GetVRegReference(invocationContext, first);
          argCount = argTypes.length + 1;
        }
      }
      Object[] arguments = new Object[argCount];
      int slotIdx;
      if (thiz == null) {
        slotIdx = 0;
      } else {
        arguments[0] = thiz;
        i += 1;
        slotIdx = 1;
      }
      for (; i < numberVRegs; i++) {
        Class<?> t = argTypes[argIdx];
        argIdx++;
        if (t.isPrimitive()) {
          Object valueObject;
          if (t == int.class) {
            valueObject = (int) InstructionListener.GetVReg(invocationContext, i);
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
          arguments[slotIdx] = valueObject;
        } else
          arguments[slotIdx] = InstructionListener.GetVRegReference(invocationContext, i);
        slotIdx++;
      }
      return arguments;
    } catch (Throwable e) {
      Albatross.log("getArguments err", e);
      return null;
    }

  }


}
