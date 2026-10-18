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
 * {@link Stalker#followFunction} 返回的句柄, 调用 {@link #unhook()} 撤销按函数追踪。
 * 不要在目标函数执行过程中 unhook(会跳过自动 unfollow)。
 */
public final class StalkerFollowHandle {

  private long nativePtr;

  StalkerFollowHandle(long nativePtr) {
    this.nativePtr = nativePtr;
  }

  public void unhook() {
    if (nativePtr == 0)
      return;
    AlbNative.stalkerUnfollowFunction(nativePtr);
    nativePtr = 0;
  }
}
