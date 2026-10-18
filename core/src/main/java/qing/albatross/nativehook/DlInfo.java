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

import static qing.albatross.nativehook.AlbNative.dlclose;
import static qing.albatross.nativehook.AlbNative.dlsym;

import java.lang.reflect.Method;

import qing.albatross.core.Albatross;

public class DlInfo {
  private long handle;

  public DlInfo(long handle) {
    this.handle = handle;
  }

  public long enumerateFunctions(SearchCallback callback) {
    return AlbNative.iterSymbol(handle, callback);
  }

  public long getModuleBase() {
    return AlbNative.getModuleBase(handle);
  }

  public long getSymbolAddress(String symbol) {
    if (handle > 4096 || handle < 0) {
      long symbolAddr = dlsym(handle, symbol);
      if (symbolAddr == 0)
        Albatross.log("can not get symbol " + symbol);
      return symbolAddr;
    }
    return 0;
  }

  public void close() {
    if (handle > 4096 || handle < 0) {
      dlclose(handle);
      handle = 0;
    }
  }

  public void backup(long symbol, Method method) {

  }


}