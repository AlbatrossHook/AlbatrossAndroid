package qing.albatross.demo;

import android.util.Log;

import java.lang.reflect.Method;

import qing.albatross.core.Albatross;

public class DemoMain {

  static boolean hook = true;

  public static boolean testEntry() {
    try {
      Method main = TestMain.class.getDeclaredMethod("main", boolean.class);
      if (Albatross.isCompiled(main)) {
        Albatross.log( "TestMain  is compiled");
        Albatross.decompileMethod(main, true);
      } else {
        Albatross.log( "TestMain  is not compiled");
      }
      if (hook) {
        TestMain a = new TestMain(1, 1);
        assert a.z == 1;
        if (!Albatross.containsFlags(Albatross.FLAG_FIELD_INVALID)) {
          boolean r = Albatross.backupField(TestMain.B3.class.getDeclaredField("i"), TestMain.B3H.class.getDeclaredField("i"));
          assert r;
        }
        long r = Albatross.entryPointFromQuickCompiledCode(TestMain.TestMainH.class.getDeclaredMethod("testCall2", long.class));
        int res = Albatross.hookClass(TestMain.TestMainH.class);
        assert res > 0;
        TestMain.main(hook);
        hook = false;
        return true;
      } else {
        TestMain.main(false);
        return false;
      }
//        Class.forName(B3H.class.getName());
//        Class.forName(B3.class.getName());

    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

}
