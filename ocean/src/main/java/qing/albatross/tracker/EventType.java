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

package qing.albatross.tracker;

public class EventType {
  public static final int EXEC = 1;
  public static final int CALL = 2;
  public static final int BLOCK = 8;
  public static final int RETURN = 0x10;
  public static final int FILE_READ = 0x20;
  public static final int FIELD_WRITE = 0x40;
  public static final int DUMP = 0x80;
  public static final int CALL_ENTER=0x100;
  public static final int ALL = 0xffff;

}
