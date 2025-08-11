package qing.albatross.demo;


import static qing.albatross.annotation.CallWay.MIRROR;

import qing.albatross.annotation.FuzzyMatch;
import qing.albatross.annotation.StaticMethodBackup;
import qing.albatross.core.Albatross;
import qing.albatross.exception.AlbatrossErr;
import qing.albatross.reflection.ReflectUtils;

public class PrimCoarseMatch {


  static class A {

    public static void method(int a, int expect) {
      Albatross.log("PrimCoarseMatch:" + a);
      assert a == expect;
    }


    @StaticMethodBackup(callWay = MIRROR, name = "method")
    public static native void methodC(@FuzzyMatch char c, int expect);

    @StaticMethodBackup(callWay = MIRROR)
    public static native void method$BackupS(@FuzzyMatch short f, int expect);


    public static int m(int v) {
      Albatross.log("PrimCoarseMatch Return:" + v);
      return v;
    }

    @StaticMethodBackup(callWay = MIRROR)
    public native static int m$BackupI(int v);

    @StaticMethodBackup(callWay = MIRROR)
    public native static @FuzzyMatch boolean m$Backup(int v);

    @StaticMethodBackup(callWay = MIRROR)
    public native static @FuzzyMatch short m$BackupS(int v);

    @StaticMethodBackup(callWay = MIRROR)
    public native static @FuzzyMatch byte m$BackupB(int v);

  }


  public static void test(boolean hook) throws AlbatrossErr, NoSuchMethodException {
    if (hook) {
      assert char.class.isPrimitive();
      assert double.class.isPrimitive();
      assert Albatross.hookClass(A.class, A.class) == 6;
      assert ReflectUtils.getPrimSize(Integer.class) == 0;
      assert !Long.class.isPrimitive();
    }
    A.method(1, 1);
    A.methodC((char) 123, 123);
    A.method$BackupS((short) 8081, 8081);
    assert A.m$BackupI(123467) == 123467;
    assert A.m$Backup(2);
    assert !A.m$Backup(0);
    assert A.m$BackupS(12345) == 12345;
    assert A.m$BackupB(12) == 12;
    byte b = A.m$BackupB(0x7f);
    assert b == 0x7f;
  }

}
