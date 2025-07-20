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

package qing.albatross.exception;

import java.lang.reflect.Field;

public class FieldException extends AlbatrossException {
  public Field backupFiled;
  public Field targetField;
  public FieldExceptionReason reason;

  public static FieldException create(Field backupFiled, Field targetField, FieldExceptionReason reason) {
    String desc;
    switch (reason) {
      case WRONG_STATIC_FIELD:
        desc = "Backup " + backupFiled + " and Target " + targetField + " must have same static property";
        break;
      case FIELD_BAN:
        desc = "not support Backup static Field";
        break;
      case WRONG_TYPE:
        desc = "Incompatible backup types. " + backupFiled.getType() + ", " + targetField.getName() + ": " + targetField.getType();
        break;
      default:
        desc = "backup field fail";
    }
    return new FieldException(backupFiled, targetField, reason, desc);
  }

  public FieldException(Field backupFiled, Field targetField, FieldExceptionReason reason, String desc) {
    super(desc);
    this.backupFiled = backupFiled;
    this.targetField = targetField;
    this.reason = reason;
  }

}
