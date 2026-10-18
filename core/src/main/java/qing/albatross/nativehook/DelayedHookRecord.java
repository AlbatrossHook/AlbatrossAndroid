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
 * 延迟 native hook 句柄：目标库/符号尚未加载时先登记，库被加载后自动安装，
 * 安装早于 dlopen 返回其调用方。注册入口见
 * {@link AlbNative#hookInstructionDelayed} 与 {@link AlbNative#hookNativeDelayed}。
 *
 * <p>与 {@link HookRecord} 的差别仅是安装时机；enter/leave 回调语义与
 * {@link AlbNative#hookInstruction} 完全一致。
 */
public class DelayedHookRecord {
  /** 等待目标库加载 */
  public static final int PENDING = 0;
  /** 已解析并安装 */
  public static final int APPLIED = 1;
  /** 库已加载但符号未找到/不可 hook，或安装失败 */
  public static final int FAILED = 2;

  long nativePtr;
  volatile long id;
  private final DelayedHookApplied callback;

  DelayedHookRecord(DelayedHookApplied callback) {
    this.callback = callback;
  }

  /** 延迟 hook id（未登记成功为 0） */
  public long getId() {
    return id;
  }

  /** 当前状态：PENDING/APPLIED/FAILED，句柄已释放返回 -1 */
  public int getState() {
    return id == 0 ? -1 : AlbNative.delayedHookStateNative(id);
  }

  /** 解析到的符号地址（未解析/失败为 0） */
  public long getAddress() {
    return id == 0 ? 0 : AlbNative.delayedHookAddressNative(id);
  }

  /** 备份（原函数跳板）；插桩型恒为 0，替换型安装成功后才非 0 */
  public long getBackup() {
    return id == 0 ? 0 : AlbNative.delayedHookBackupNative(id);
  }

  /** 取消登记；已安装则先卸载。同一句柄只生效一次 */
  public boolean unHook() {
    long handle = nativePtr;
    if (handle != 0) {
      nativePtr = 0;
      return AlbNative.unHookDelayedNative(handle);
    }
    return false;
  }

  /** native 安装完成回调入口，由 JNI 调用 */
  @SuppressWarnings("unused")
  void dispatchApplied(long hookId, int state, long address, long backup) {
    if (hookId != 0)
      id = hookId;
    if (callback != null)
      callback.onApplied(this);
  }
}
