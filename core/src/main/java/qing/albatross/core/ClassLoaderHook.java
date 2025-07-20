
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


package qing.albatross.core;

import qing.albatross.annotation.ConstructorBackup;
import qing.albatross.annotation.ConstructorHook;
import qing.albatross.annotation.ConstructorHookBackup;
import qing.albatross.annotation.TargetClass;

@TargetClass(ClassLoader.class)
public class ClassLoaderHook {

  //  @ConstructorBackup
//  static native void BaseDexClassLoader(BaseDexClassLoader baseDexClassLoader, String dexPath,
//                                        String librarySearchPath, ClassLoader parent, ClassLoader[] sharedLibraryLoaders,
//                                        boolean isTrusted);
//
//  @ConstructorHook
//  static void BaseDexClassLoader$Hook(BaseDexClassLoader baseDexClassLoader, String dexPath,
//                                      String librarySearchPath, ClassLoader parent, ClassLoader[] sharedLibraryLoaders,
//                                      boolean isTrusted) {
//    BaseDexClassLoader(baseDexClassLoader, dexPath, librarySearchPath, parent, sharedLibraryLoaders, isTrusted);
//  }


  @ConstructorBackup
  @ConstructorHook
  static void init(ClassLoader loader, Void unused, ClassLoader parent) {
    init(loader, unused, parent);
    Albatross.appendLoader(loader);
  }

  @ConstructorBackup
  @ConstructorHook
  static void init(ClassLoader loader, Void unused, String name, ClassLoader parent) {
    init(loader, unused, name, parent);
    Albatross.appendLoader(loader);
  }

  @ConstructorBackup
  @ConstructorHook
  static void initM(ClassLoader loader, ClassLoader parent, boolean nullAllowed) {
    initM(loader, parent, nullAllowed);
    Albatross.appendLoader(loader);
  }

  //aot code may inline other constructor,so hook this constructor also.
  @ConstructorHookBackup
  private static void init(ClassLoader loader, ClassLoader parent) {
    init(loader, parent);
    Albatross.appendLoader(loader);
  }

}
