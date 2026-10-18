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
 * stalker 块编译期 transformer: 每个基本块首次编译时回调一次,
 * 可遍历块内指令决定保留({@link StalkerIterator#keep()})或丢弃,
 * 并可在指定指令前插入执行期回调({@link StalkerIterator#putCallout(StalkerCallout)})。
 * <p>
 * 回调发生在被追踪线程上, 内部已做重入保护: 回调执行期间新编译的块
 * 按默认策略(保留全部指令)处理, 不会再次进入本回调。
 */
public interface StalkerTransformer {

  void transform(StalkerIterator iterator);
}
