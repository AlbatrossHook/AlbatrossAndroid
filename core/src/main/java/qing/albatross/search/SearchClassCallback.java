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
package qing.albatross.search;


public interface SearchClassCallback {

  int STOP = 0;
  int CONTINUE = 1;
  int SKIP_CURRENT_DEX = 2;

  int SCOPE_PLATFORM = 1;
  int SCOPE_APPLICATION = 2;
  int SCOPE_ALL = SCOPE_APPLICATION | SCOPE_PLATFORM;

  int match(Class<?> o, long pos);
}
