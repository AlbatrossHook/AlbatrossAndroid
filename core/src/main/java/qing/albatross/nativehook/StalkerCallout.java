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
 * stalker 执行期回调, 由 {@link StalkerIterator#putCallout} 插入,
 * 生成代码执行到插入点时触发, 可读写寄存器改变后续执行。
 * 回调发生在被追踪线程上, 应尽量轻量, 避免触发大量新代码编译。
 */
public interface StalkerCallout {

  void onCall(NativeCpuContext cpuContext);
}
