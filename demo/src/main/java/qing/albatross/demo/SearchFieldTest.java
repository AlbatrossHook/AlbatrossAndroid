package qing.albatross.demo;


import android.app.Activity;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import dalvik.system.BaseDexClassLoader;
import qing.albatross.annotation.TargetClass;
import qing.albatross.core.Albatross;
import qing.albatross.reflection.BooleanFieldDef;
import qing.albatross.search.FieldCallback;
import qing.albatross.search.FieldSearchCallback;

public class SearchFieldTest {

  @TargetClass(Activity.class)
  static class ActivityH {
    static BooleanFieldDef mResumed;

    static {
      Albatross.hookClass();
    }

  }


  public static void test() throws NoSuchFieldException {
    Field field = FieldSearchCallback.class.getDeclaredField("count");
    List<Object[]> methods = new ArrayList<>();
    int count = Albatross.searchField(field, FieldCallback.FIELD_ALL, (m, a, pos) -> {
      methods.add(new Object[]{m, pos});
      return true;
    }, false, false);
    assert count == methods.size();
    methods.clear();
    int count2 = Albatross.searchField(field, FieldCallback.FIELD_GET, (m, a, pos) -> {
      assert a == FieldCallback.FIELD_GET;
      methods.add(new Object[]{m, pos});
      return true;
    }, false, true);
    methods.clear();
    int count3 = Albatross.searchField(field, FieldCallback.FIELD_SET, (m, a, pos) -> {
      assert a == FieldCallback.FIELD_SET;
      methods.add(new Object[]{m, pos});
      return true;
    }, false, false);
    assert count == count2 + count3;
    methods.clear();
    count = Albatross.searchFieldClassRef(ActivityH.mResumed.getField(), Activity.class, FieldCallback.FIELD_ALL, (m, a, pos) -> {
      assert !(m.getDeclaringClass().getClassLoader() instanceof BaseDexClassLoader);
      methods.add(new Object[]{m, pos});
      return true;
    });
    assert count > 0;
    methods.clear();
    count = Albatross.searchField(ActivityH.mResumed.getField(), FieldCallback.FIELD_ALL, (m, a, pos) -> {
      assert !(m.getDeclaringClass().getClassLoader() instanceof BaseDexClassLoader);
      methods.add(new Object[]{m, pos});
      return true;
    }, false, true);
    assert count > 0;
    assert count == methods.size();
  }
}
