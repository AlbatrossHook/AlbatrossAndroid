package qing.albatross.demo;

import android.app.Activity;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.widget.TextView;

import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import qing.albatross.core.Albatross;
import qing.albatross.search.SearchClassCallback;

public class SearchCallerTest {


  public static void test() throws NoSuchMethodException {
    if (Build.VERSION.SDK_INT < 26)
      return;
    Method startActivity = Activity.class.getDeclaredMethod("startActivityForResult", Intent.class, int.class, Bundle.class);
    List<Member> methods = new ArrayList<>();
    Albatross.searchMethodCallerFromClass(startActivity, Activity.class, (m, i) -> {
      methods.add(m);
      return true;
    }, true);
    assert !methods.isEmpty();
    for (Member m : methods) {
      assert m.getDeclaringClass() == Activity.class;
    }
    methods.clear();
    Method addToVisit = Albatross.class.getDeclaredMethod("addToVisit", Class.class);
    int count = Albatross.searchMethodCaller(Albatross.class, addToVisit, (m, i) -> {
      methods.add(m);
      return true;
    });
    assert !methods.isEmpty();
    assert methods.size() > 1;
    assert count == methods.size();
    for (Member m : methods) {
      assert m.getDeclaringClass() == Albatross.class;
    }
    methods.clear();
    count = Albatross.searchMethodCaller(Albatross.class, addToVisit, (m, i) -> {
      methods.add(m);
      return false;
    });
    assert count == 1;

    count = Albatross.searchMethodCaller(String.class, addToVisit, (m, i) -> {
      return true;
    });
    assert count == 0;
    methods.clear();
    count = Albatross.searchMethodCaller(Albatross.class, Class.class.getDeclaredMethod("isPrimitive"), (m, i) -> {
      methods.add(m);
      return true;
    });
    assert count > 0;
    for (Member m : methods) {
      assert m.getDeclaringClass() == Albatross.class;
    }
    methods.clear();
    count = Albatross.searchMethodCaller(TextView.class.getDeclaredMethod("setText", CharSequence.class), (m, i) -> {
      methods.add(m);
      return true;
    }, true, SearchClassCallback.SCOPE_ALL);
    assert count > 0;
  }


}
