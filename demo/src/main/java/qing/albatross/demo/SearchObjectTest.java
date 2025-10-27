package qing.albatross.demo;

import android.os.Build;

import java.lang.reflect.Method;

import qing.albatross.core.Albatross;

public class SearchObjectTest {


  static class A {

  }

  static class B extends A {
  }

  static void test() throws NoSuchMethodException {
    Method method = Albatross.class.getDeclaredMethod("insLayoutMeasure", int.class, int.class, int.class);
    int size = Albatross.getMethodCodeSize(method);
    assert size == 3;
    if (Build.VERSION.SDK_INT < 29)
      return;
    A a = new A();
    boolean[] find = {false};
    int count = Albatross.searchObject(A.class, (o, i) -> {
      if (o == a) {
        find[0] = true;
        return false;
      }
      return true;
    });
    assert count > 0;
    assert find[0];
    A b = new A();
    int count2 = Albatross.searchObject(A.class, (o, i) -> o == b);
    assert count + 1 == count2;
    B bb = new B();
    find[0] = false;
    count = Albatross.searchObject(A.class, (o, i) -> {
      if (o == bb) {
        find[0] = true;
        return false;
      }
      return true;
    });
    assert count == count2 + 1;
    assert find[0];
  }


}
