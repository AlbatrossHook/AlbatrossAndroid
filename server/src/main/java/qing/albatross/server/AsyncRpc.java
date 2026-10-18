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

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标记该 RPC 方法可以被异步执行：客户端以 MSG_ASYNC_CALL 调用时，agent 的
 * 单线程 epoll 循环把它交给工作线程（注册期经 registerMethod 传入 async=true，
 * native 侧 mark_async）。用于可能卡死的高危方法（如 stalker trace 的 arm），
 * 卡死时只占住工作线程并由超时代答放走客户端，循环线程与其它客户端不受影响。
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface AsyncRpc {
}
