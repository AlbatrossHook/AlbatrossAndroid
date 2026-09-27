package qing.albatross.demo;

import static qing.albatross.search.SearchClassCallback.CONTINUE;

import android.app.Activity;
import android.app.NativeActivity;

import java.util.ArrayList;
import java.util.List;

import qing.albatross.core.Albatross;
import qing.albatross.search.SearchClassCallback;

public class SearchSubClassTest {

  public static void test() {
    List<Class<? extends Activity>> activities = new ArrayList<>();
    int count = Albatross.searchSubClass((c, l) -> {
      activities.add(c);
      return CONTINUE;
    }, Activity.class, SearchClassCallback.SCOPE_ALL);
    assert activities.size() > 1;
    assert !activities.contains(Activity.class);
    assert count == activities.size();
    Albatross.log("Activity subclass count:" + count);
    assert activities.contains(NativeActivity.class);
    List<Class<? extends CharSequence>> strings = new ArrayList<>();
    count = Albatross.searchSubClass((c, l) -> {
      strings.add(c);
      return CONTINUE;
    }, CharSequence.class, SearchClassCallback.SCOPE_ALL);
    assert strings.size() > 1;
    Albatross.log("CharSequence subclass count:" + count);
    assert strings.contains(String.class);
  }
}
