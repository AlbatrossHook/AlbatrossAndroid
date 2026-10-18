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

public class Address {
  long address;
  long size;

  public void clear() {
    if (address != 0) {
      address = 0;
      size = 0;
    }
  }

  public long getAddress() {
    return address;
  }

  public long getSize() {
    return size;
  }


  public void delete() {
    if (address != 0) {
      AlbNative.freeAddress(address);
      address = 0;
    }
  }

  public static Address malloc(int size, boolean clear) {
    long address = clear ? AlbNative.callocNative(size) : AlbNative.mallocNative(size);
    if (address != 0) {
      Address address1 = new Address();
      address1.address = address;
      address1.size = size;
      return address1;
    }
    return null;
  }


  boolean readable() {
    return AlbNative.readableNative(address);
  }

  /** 读取指定长度的原始字节(不经过字符串转换, 用于字节级校验)。 */
  public byte[] readBytes(int len) {
    return AlbNative.readBytes(address, len);
  }

  public String readString(int maxLen) {
    if (size != 0 && maxLen > size)
      maxLen = (int) size;
    return AlbNative.readString(address, maxLen);
  }

  public boolean writeString(String s) {
    if (s == null) {
      return false;
    }
    assert size == 0 || s.length() < size;
    return AlbNative.writeString(address, s);
  }


  @Override
  public String toString() {
    return "Address{" +
        "address=" + address +
        '}';
  }
}
