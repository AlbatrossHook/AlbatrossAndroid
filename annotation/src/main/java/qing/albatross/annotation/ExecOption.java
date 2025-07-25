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

package qing.albatross.annotation;

public class ExecOption {
  public static final int JIT_OSR = 1;
  public static final int JIT_BASELINE = 2;
  public static final int JIT_OPTIMIZED = 4;

  public static final int DO_NOTHING = 0;
  public static final int DECOMPILE = 8;
  public static final int INTERPRETER = DECOMPILE;
  public static final int DEFAULT_OPTION = 0x10;

  public static final int RECOMPILE_OSR = JIT_OSR | DECOMPILE;
  public static final int RECOMPILE_BASELINE = JIT_BASELINE | DECOMPILE;
  public static final int RECOMPILE_OPTIMIZED = JIT_OPTIMIZED | DECOMPILE;
  public static final int DISABLE_AOT = 0x20;
  public static final int DISABLE_JIT = 0x40;
  public static final int AOT = 0x80;
  public static final int NATIVE_CODE = AOT | JIT_OPTIMIZED;
}
