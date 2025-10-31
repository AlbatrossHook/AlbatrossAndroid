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

import java.lang.reflect.Member;

public class MethodSearchCallback implements SearchCallback<Member> {

  public int count;
  public SearchCallback<Member> callback;
  public boolean carryOn;

  public MethodSearchCallback(SearchCallback<Member> callback) {
    this.callback = callback;
    count = 0;
    carryOn = true;
  }

  @Override
  public boolean match(Member o, int pos) {
    count++;
    carryOn = callback.match(o, pos);
    return carryOn;
  }
}
