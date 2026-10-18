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

import java.util.ArrayList;
import java.util.List;

/**
 * stalker 追踪事件。事件由 native 侧批量编码为 long 数组(每事件 4 槽:
 * type/a/b/c), 投递线程取出后由此类解码。
 */
public final class StalkerEvent {

  public enum Type {
    NOTHING(0), CALL(1), RET(2), EXEC(4), BLOCK(8), COMPILE(16),
    /**
     * 会话被框架主动停止(超时/事件数上限)时插入的标记事件: 出现在事件流末尾
     * 表示追踪窗口已结束, {@link #depth} 此时是停止原因(REASON_*)。不是真实执行事件。
     */
    STOPPED(0xFF);

    final int mask;

    Type(int mask) {
      this.mask = mask;
    }

    static Type fromMask(int mask) {
      for (Type type : values()) {
        if (type.mask == mask)
          return type;
      }
      return NOTHING;
    }
  }

  /** STOPPED 事件的停止原因, 与 native 侧 ALB_STALKER_STOP_* 对应 */
  public static final int REASON_NONE = 0;
  public static final int REASON_TIMEOUT = 1;
  public static final int REASON_MAX_EVENTS = 2;

  public final Type type;
  /** CALL/RET: 调用点地址; EXEC: 指令地址; BLOCK/COMPILE: 块起始地址 */
  public final long location;
  /** CALL/RET: 目标地址; BLOCK/COMPILE: 块结束地址 */
  public final long target;
  /** CALL/RET: 调用深度; STOPPED: 停止原因(REASON_*) */
  public final int depth;

  /** STOPPED 标记事件的停止原因(非 STOPPED 事件无意义) */
  public int stopReason() {
    return depth;
  }

  public boolean isStopped() {
    return type == Type.STOPPED;
  }

  private StalkerEvent(Type type, long location, long target, int depth) {
    this.type = type;
    this.location = location;
    this.target = target;
    this.depth = depth;
  }

  public long start() {
    return location;
  }

  public long end() {
    return target;
  }

  @Override
  public String toString() {
    return "StalkerEvent{" + type + ", location=0x" + Long.toHexString(location)
        + ", target=0x" + Long.toHexString(target) + ", depth=" + depth + "}";
  }

  /** 解码 native 侧批量传入的扁平 long 数组, 每事件占 4 个槽位。 */
  public static List<StalkerEvent> decode(long[] encoded) {
    List<StalkerEvent> events = new ArrayList<>(encoded.length / 4);
    for (int i = 0; i + 3 < encoded.length; i += 4) {
      events.add(new StalkerEvent(Type.fromMask((int) encoded[i]),
          encoded[i + 1], encoded[i + 2], (int) encoded[i + 3]));
    }
    return events;
  }
}
