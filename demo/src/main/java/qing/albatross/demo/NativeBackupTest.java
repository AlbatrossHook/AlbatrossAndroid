package qing.albatross.demo;

import android.content.Context;

import qing.albatross.core.Albatross;
import qing.albatross.nativehook.Address;
import qing.albatross.nativehook.Libc;

public class NativeBackupTest {

  public static void test() {
    Context context = Albatross.currentApplication();
    String filePath = context.getDataDir().getAbsoluteFile() + "/native.log";
    int fd = Libc.open(filePath, Libc.O_CREAT | Libc.O_TRUNC | Libc.O_WRONLY, Integer.parseInt("644", 8));
    if (fd > 0) {
      Address address = Address.malloc(64, true);
      assert address != null;
      try {
        String content = "malloc write\n";
        address.writeString(content);
        long n = Libc.write(fd, address.getAddress(), content.length());
        assert n == content.length() : n;
        content = "string write\n";
        n = Libc.write(fd, content, content.length());
        assert n == content.length() : n;
        content = "byte write\n";
        byte[] bs = content.getBytes();
        n = Libc.write(fd, bs, bs.length);
        Libc.close(fd);
        fd = -1;
        assert n == bs.length : n;
        fd = Libc.open(filePath, Libc.O_RDONLY, 0);
        long nRead = Libc.read(fd, address.getAddress(), address.getSize() - 1);
        if (nRead > 0) {
          String s = address.readString(64);
          assert s.length() >= nRead;
        }
      } finally {
        address.delete();
        if (fd > 0) {
          Libc.close(fd);
        }
      }
    }
  }


}
