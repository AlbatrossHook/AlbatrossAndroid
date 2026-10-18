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

/** {@link StalkerIterator} 的内部实现, 仅在 native 回调栈上创建。 */
final class StalkerIteratorImpl implements StalkerIterator {

  private final long iterator;
  private boolean consumedAnyInstruction;
  private boolean keptAnyInstruction;

  StalkerIteratorImpl(long iterator) {
    this.iterator = iterator;
  }

  @Override
  public boolean next() {
    boolean hasNext = AlbNative.stalkerIteratorNext(iterator);
    if (hasNext)
      consumedAnyInstruction = true;
    return hasNext;
  }

  @Override
  public long address() {
    return AlbNative.stalkerIteratorAddress(iterator);
  }

  @Override
  public int size() {
    return AlbNative.stalkerIteratorSize(iterator);
  }

  @Override
  public String mnemonic() {
    return AlbNative.stalkerIteratorMnemonic(iterator);
  }

  @Override
  public String operands() {
    return AlbNative.stalkerIteratorOperands(iterator);
  }

  @Override
  public void keep() {
    keptAnyInstruction = true;
    AlbNative.stalkerIteratorKeep(iterator);
  }

  @Override
  public void putCallout(StalkerCallout callout) {
    AlbNative.stalkerIteratorPutCallout(iterator, callout);
  }

  /**
   * 兜底: transformer 结束后若从未 keep 任何指令, 按默认策略保留全部指令,
   * 避免空块破坏执行流。
   */
  void ensureTransformed() {
    if (keptAnyInstruction)
      return;
    while (next())
      keep();
    // 未消费任何指令的块同样按原样保留: native 侧空 transform 会保留块首指令
    if (!consumedAnyInstruction) {
      if (AlbNative.stalkerIteratorNext(iterator)) {
        AlbNative.stalkerIteratorKeep(iterator);
      }
    }
  }
}
