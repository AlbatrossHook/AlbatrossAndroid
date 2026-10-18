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

/**
 * stalker 基本块指令迭代器, 在 {@link StalkerTransformer#transform} 回调中使用。
 * 通过 {@link #next()} 前进, 未调用 {@link #keep()} 的指令会被丢弃,
 * 因此典型用法为: {@code while (iterator.next()) { ...; iterator.keep(); }}。
 */
public interface StalkerIterator {

  /** 前进到下一条指令, 无更多指令(或缓冲空间耗尽)时返回 false。 */
  boolean next();

  /** 当前指令地址。 */
  long address();

  /** 当前指令字节长度。 */
  int size();

  /** 当前指令助记符, 如 "bl"、"mov"。 */
  String mnemonic();

  /** 当前指令操作数文本, 如 "x0, #0x10"。 */
  String operands();

  /** 保留当前指令(重定位进生成代码), 未 keep 的指令被丢弃。 */
  void keep();

  /**
   * 在当前指令前插入执行期回调: 生成代码每次执行到该点时回调一次,
   * 可通过 {@link NativeCpuContext} 读写寄存器。
   * arm32 暂不支持, 抛 UnsupportedOperationException。
   */
  void putCallout(StalkerCallout callout);
}
