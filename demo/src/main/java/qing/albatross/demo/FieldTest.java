package qing.albatross.demo;

import android.os.Build;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import qing.albatross.annotation.FieldRef;
import qing.albatross.annotation.TargetClass;
import qing.albatross.core.Albatross;

public class FieldTest {
  static class B {
    public int dd = 123456;
    public int i = 2;
    public int z = 111111111;
  }

  @TargetClass(B.class)
  static class BH {
    @FieldRef("i")
    int iback = 4;
  }

  static void testField(int i, BH z1, B b) {
    z1.iback = i;
    assert b.i == i;
    b.i = i + 1;
    assert z1.iback == i + 1;
  }

  public static void test(boolean hook) {
    B b = new B();
    assert b.i == 2;
    try {
      Field iback = BH.class.getDeclaredField("iback");
      if (hook) {
        assert Albatross.backupField(B.class.getDeclaredField("i"), iback);
        Method method = FieldTest.class.getDeclaredMethod("filedBackupTest", Field.class, BH.class, B.class);
        Albatross.decompileMethod(method, false);
        Albatross.compileMethod(method);
      } else {
        assert !Albatross.backupField(B.class.getDeclaredField("i"), iback);
      }
      assert !hook || Albatross.hookClass(BH.class, B.class) == 0;
      BH bh = Albatross.convert(b, BH.class);
      long address = Albatross.getObjectAddress(bh);
      filedBackupTest(iback, bh, b);
      for (int i = 0; i < 1000; i++)
        testField(i, bh, b);
      for (int i = 0; i < 1000; i++)
        testField(i + 1000, bh, b);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
    Albatross.log("end field test");
  }

  private static void filedBackupTest(Field iback, BH bh, B b) throws NoSuchFieldException, IllegalAccessException {
    Field iback2 = BH.class.getDeclaredField("iback");
    if (Build.VERSION.SDK_INT > 25) {
      int backvalue2 = (int) iback.get(bh);
      int z1_iback_value = bh.iback;
      assert z1_iback_value == 2;
    }

//      long address2 = Albatross.getObjectAddress(b);
    bh.iback = 3;
    assert b.i == 3;
  }
}
