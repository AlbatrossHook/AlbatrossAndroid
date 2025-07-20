package qing.albatross.demo;

import qing.albatross.annotation.MethodBackup;
import qing.albatross.annotation.MethodHookBackup;
import qing.albatross.annotation.TargetClass;
import qing.albatross.core.Albatross;
import qing.albatross.exception.AlbatrossErr;
import qing.albatross.exception.NotNativeBackupErr;
import qing.albatross.exception.RedundantFieldErr;
import qing.albatross.exception.RedundantMethodErr;
import qing.albatross.exception.VirtualCallBackupErr;

public class HookerStructErrTest {

  static class A {
    int i;

    int demo() {
      return 1;
    }
  }

  @TargetClass(A.class)
  static class AH {
    public int i;
  }

  @TargetClass(A.class)
  static class AH2 {
    private int demo() {
      return demo();
    }
  }

  @TargetClass(A.class)
  static class AH3 {
    @MethodBackup
    private int demo() {
      return 1;
    }
  }

  @TargetClass(A.class)
  static class AH4 {
    @MethodBackup
    public native int demo();
  }

  @TargetClass(A.class)
  static class AH5 {
    @MethodHookBackup
    public int demo() {
      return demo();
    }
  }


  static void test(boolean hook) {
    if (!hook)
      return;

    if (Albatross.isFieldEnable()) {
      try {
        Albatross.hookClass(AH.class);
        throw new RuntimeException("should not reach there");
      } catch (AlbatrossErr e) {
        assert e instanceof RedundantFieldErr;
      }
    }
    try {
      Albatross.hookClass(AH2.class);
      throw new RuntimeException("should not reach there");
    } catch (AlbatrossErr e) {
      assert e instanceof RedundantMethodErr;
    }

    try {
      Albatross.hookClass(AH3.class);
      throw new RuntimeException("should not reach there");
    } catch (AlbatrossErr e) {
      assert e instanceof NotNativeBackupErr;
    }
    try {
      Albatross.hookClass(AH4.class);
      throw new RuntimeException("should not reach there");
    } catch (AlbatrossErr e) {
      assert e instanceof VirtualCallBackupErr;
    }
    try {
      Albatross.hookClass(AH5.class);
      throw new RuntimeException("should not reach there");
    } catch (AlbatrossErr e) {
      assert e instanceof VirtualCallBackupErr;
    }
  }

}
