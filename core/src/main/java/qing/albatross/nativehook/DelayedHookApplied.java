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
 * 延迟 hook 安装完成回调（成功或失败都会回调一次）。
 *
 * <p>在触发目标库加载的线程上同步执行（注册时库已加载则在注册调用内执行），
 * 早于 {@code System.loadLibrary}/{@code dlopen} 返回其调用方；回调里可以调用
 * 框架 API（含 {@link DelayedHookRecord#unHook()}），但不要再触发加载。
 */
public interface DelayedHookApplied {
  void onApplied(DelayedHookRecord record);
}
