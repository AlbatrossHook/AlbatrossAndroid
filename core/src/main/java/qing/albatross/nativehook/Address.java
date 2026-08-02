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
