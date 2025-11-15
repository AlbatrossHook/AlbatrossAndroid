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
package qing.albatross.common;

import android.util.ArraySet;

import java.util.Set;

import qing.albatross.core.Albatross;

public class ThreadConfig {

  public static final Set<Integer> notTraceThreads = new ArraySet<>();
  private static final ThreadLocal<StringBuilder> threadBuilder = new ThreadLocal<>();
  private static final ThreadLocal<String> threadInfo = new ThreadLocal<>();


  //for ocean tracker only，Avoid frequently creating objects, which can cause memory jitter and make the app lag due to vm gc
  public static StringBuilder stringBuilder() {
    StringBuilder sb = threadBuilder.get();
    if (sb == null) {
      sb = new StringBuilder(8192);
      threadBuilder.set(sb);
    }
    sb.setLength(0);
    return sb;
  }

  public static String myId() {
    String info = threadInfo.get();
    if (info == null) {
      info = Thread.currentThread().getName() + ":" + Albatross.getTid();
      threadInfo.set(info);
    }
    return info;
  }

  public static void notTraceMe() {
    int tid = Albatross.getTid();
    notTraceThreads.add(tid);
  }

  public static void notifyLeave() {
    int tid = Albatross.getTid();
    if (notTraceThreads.contains(tid)) {
      Albatross.getMainHandler().postDelayed(() -> {
        notTraceThreads.remove(tid);
      }, 1000);
    }
    threadBuilder.remove();
    threadInfo.remove();
  }

  public static boolean canTraceMe() {
    if (notTraceThreads.isEmpty())
      return true;
    int tid = Albatross.getTid();
    return !notTraceThreads.contains(tid);
  }

  public static boolean canTraceThread(int tid) {
    if (notTraceThreads.isEmpty())
      return true;
    return !notTraceThreads.contains(tid);
  }
}
