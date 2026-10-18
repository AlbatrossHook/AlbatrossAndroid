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

/**
 * 免签名动态 hook 的回调（类似 pine 的 MethodHook）。
 * 通过 {@link Albatross#hookMethod(Member, MethodHook)} 对任意 {@link Method} 生效，
 * 无需预先编写与目标方法签名匹配的 hook/backup 方法。
 *
 * <p>调用时序（每次目标方法被调用）：
 * <ol>
 *   <li>按注册顺序调用 {@link #beforeCall}；返回 {@code false} 表示跳过原方法
 *       （在 beforeCall 中 {@link CallFrame#setResult}/{@link CallFrame#setThrowable}
 *       也会导致跳过）。返回 {@code true} 时框架会自动调用一次原方法。</li>
 *   <li>（未跳过时）执行原方法。</li>
 *   <li>按注册的逆序调用 {@link #afterCall}，可在其中读取/改写结果。</li>
 * </ol>
 *
 * <p>注意：返回 {@code true} 时不要在 beforeCall 里调用 {@link CallFrame#invokeOriginal()}，
 * 否则原方法会执行两次；需要主动控制原方法执行时返回 {@code false}。
 * 回调抛出的异常会跳过原方法并在 afterCall 之后向调用者传播。
 */
public abstract class MethodHookCallback {

  /**
   * 目标方法调用前回调。
   *
   * @param frame 当前调用的参数/结果视图
   * @return {@code true} 继续执行原方法；{@code false} 跳过原方法
   */
  public boolean beforeCall(CallFrame frame) throws Throwable {
    return true;
  }

  /** 目标方法返回后回调（跳过原方法时也会调用）。 */
  public void afterCall(CallFrame frame) throws Throwable {
  }
}
