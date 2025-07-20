
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


package qing.albatross.server;

import android.accounts.Account;

import java.util.Collection;
import java.util.Map;
import java.util.Set;

public class JsonFormatter {
  public static final String SKIP_PARSE = "#_#Q";
  private static final String SKIP_PARSE_CHECK = SKIP_PARSE + "\"";

  public static String fmt(Object object) {
    return fmt(object, false);
  }

  public static String string(String value) {
    StringBuilder out = new StringBuilder();
    string(out, value);
    return out.toString();
  }

  public static void string(StringBuilder out, String value) {
    if (value.startsWith(SKIP_PARSE)) {
      out.append(value.substring(SKIP_PARSE.length()));
      return;
    }
    out.append("\"");
    for (int i = 0, length = value.length(); i < length; i++) {
      char c = value.charAt(i);

      /*
       * From RFC 4627, "All Unicode characters may be placed within the
       * quotation marks except for the characters that must be escaped:
       * quotation mark, reverse solidus, and the control characters
       * (U+0000 through U+001F)."
       */
      switch (c) {
        case '"':
        case '\\':
        case '/':
          out.append('\\').append(c);
          break;
        case '\t':
          out.append("\\t");
          break;
        case '\b':
          out.append("\\b");
          break;
        case '\n':
          out.append("\\n");
          break;
        case '\r':
          out.append("\\r");
          break;

        case '\f':
          out.append("\\f");
          break;
        default:
          if (c <= 0x1F) {
            out.append(String.format("\\u%04x", (int) c));
          } else {
            out.append(c);
          }
          break;
      }
    }
    out.append("\"");
  }

  public static String fmt(Object object, boolean keepString) {
    if (object instanceof String) {
      if (keepString)
        return (String) object;
      return string((String) object);
    }
    StringBuilder builder = new StringBuilder();
    fmtObject(object, builder, keepString);
    return builder.toString();
  }

  public static void fmtObject(Object obj, StringBuilder builder, boolean keepString) {
    if (obj == null) {
      builder.append("null");
      return;
    }
    if (obj instanceof Collection) {
      fmtList((Collection) obj, builder, keepString);
    } else if (obj instanceof Map) {
      fmtMap((Map) obj, builder, keepString);
    } else if (obj instanceof Boolean || obj instanceof Number) {
      builder.append(obj.toString());
    } else if (obj.getClass().isArray()) {
      String className = obj.getClass().getName();
      if (className.length() == 2) {
        fmtPrimaryArray(obj, builder, className);
      } else
        fmtArray((Object[]) obj, builder, keepString);
    } else {
      String s;
      if (obj instanceof Account) {
        Account account = (Account) obj;
        s = account.name + "|" + account.type;
      } else
        s = obj.toString();
      if (keepString)
        builder.append(s);
      else
        string(builder, s);
    }
  }

  public static void fmtList(Collection<?> list, StringBuilder builder, boolean keepString) {
    builder.append("[");
    if (!list.isEmpty()) {
      for (Object object : list) {
        fmtObject(object, builder, keepString);
        builder.append(",");
      }
      builder.deleteCharAt(builder.length() - 1);
    }
    builder.append("]");
  }

  public static void fmtArray(Object[] list, StringBuilder builder, boolean keepString) {
    builder.append("[");
    if (list.length > 0) {
      for (Object object : list) {
        fmtObject(object, builder, keepString);
        builder.append(",");
      }
      builder.deleteCharAt(builder.length() - 1);
    }
    builder.append("]");
  }


  public static void fmtPrimaryArray(Object obj, StringBuilder builder, String className) {
    builder.append("[");
    switch (className) {
      case "[B": {
        byte[] list = (byte[]) obj;
        if (list.length > 0) {
          for (byte object : list) {
            builder.append(object + ",");
          }
          builder.deleteCharAt(builder.length() - 1);
        }
        break;
      }
      case "[C": {
        char[] list = (char[]) obj;
        if (list.length > 0) {
          for (char object : list) {
            builder.append(object + ",");
          }
          builder.deleteCharAt(builder.length() - 1);
        }
        break;
      }
      case "[S": {
        short[] list = (short[]) obj;
        if (list.length > 0) {
          for (short object : list) {
            builder.append(object + ",");
          }
          builder.deleteCharAt(builder.length() - 1);
        }
        break;
      }
      case "[I": {
        int[] list = (int[]) obj;
        if (list.length > 0) {
          for (int object : list) {
            builder.append(object + ",");
          }
          builder.deleteCharAt(builder.length() - 1);
        }
        break;
      }
      case "[J": {
        long[] list = (long[]) obj;
        if (list.length > 0) {
          for (long object : list) {
            builder.append(object + ",");
          }
          builder.deleteCharAt(builder.length() - 1);
        }
        break;
      }
    }
    builder.append("]");
  }

  public static void fmtMap(Map map, StringBuilder builder, boolean keepString) {
    builder.append("{");
    if (!map.isEmpty()) {
      Set<Map.Entry> set = map.entrySet();
      for (Map.Entry entry : set) {
        builder.append("\"");
        builder.append(entry.getKey().toString());
        builder.append("\"");
        builder.append(":");
        fmtObject(entry.getValue(), builder, keepString);
        builder.append(",");
      }
      builder.deleteCharAt(builder.length() - 1);
    }
    builder.append("}");
  }
}
