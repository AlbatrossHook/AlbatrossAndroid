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

public class CompileOption {
  public static final int COMPILE_OSR = 1;
  public static final int COMPILE_BASELINE = 2;
  public static final int COMPILE_OPTIMIZED = 4;

  public static final int COMPILE_NONE = 0;
  public static final int COMPILE_DECOMPILE = 8;
  public static final int COMPILE_DEFAULT = 0x10;

  public static final int COMPILE_OSR_JIT = COMPILE_OSR | COMPILE_DECOMPILE;
  public static final int COMPILE_BASELINE_JIT = COMPILE_BASELINE | COMPILE_DECOMPILE;
  public static final int COMPILE_OPTIMIZED_JIT = COMPILE_OPTIMIZED | COMPILE_DECOMPILE;
  public static final int COMPILE_DISABLE_AOT = 0x20;
  public static final int COMPILE_DISABLE_JIT = 0x40;
}
