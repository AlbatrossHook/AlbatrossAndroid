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

import static qing.albatross.annotation.ExecOption.DEFAULT_OPTION;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;


@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface MethodHook {
  String[] value() default {};
  String[] name() default {};
  Class<?> targetClass() default TargetClass.class;
  String[] className() default {};
  int hookerExec() default DEFAULT_OPTION;
  boolean isStatic() default false;
  boolean required() default false;
  byte option() default DefOption.DEFAULT;
  byte minSdk() default 0;
  byte maxSdk() default 0;
}