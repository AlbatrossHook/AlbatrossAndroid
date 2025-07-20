package qing.albatross.demo;

import android.os.Build;
import android.view.View;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import qing.albatross.core.Albatross;

public class ApiTest {

  public static void test(boolean hook) {
    if (Build.VERSION.SDK_INT <= 28)
      return;
    if (hook)
      test2();
    Class<?> Reflection = null;
    Method method = null;
    try {
      Reflection = Class.forName("sun.reflect.Reflection");
      method = Reflection.getDeclaredMethod("getCallerClass");
    } catch (Exception e) {
    }
    assert method == null;
    assert Reflection != null;
    Albatross.transactionBegin();
    Albatross.transactionEnd(true);
    try {
      method = Reflection.getDeclaredMethod("getCallerClass");
    } catch (Exception e) {
    }
    assert method == null;

    Albatross.transactionBegin();
    try {
      method = Reflection.getDeclaredMethod("getCallerClass");
    } catch (Exception e) {
    }
    Albatross.transactionEnd(true);
    assert method == null;
    Albatross.transactionBegin();
    Albatross.addToVisit(Reflection);
    try {
      method = Reflection.getDeclaredMethod("getCallerClass");
    } catch (Exception e) {
      e.printStackTrace();
    }
    assert method != null;
    Albatross.transactionEnd(true);
    try {
      Reflection.getDeclaredMethod("getCallerClass");
      method = null;
    } catch (Exception e) {
    }
    assert method != null;
  }

  public static void test2() {
    Field isVisibleToUser = null;
    Class<?> c;
    try {
      c = Class.forName("android.view.View$ListenerInfo");
    } catch (ClassNotFoundException e) {
      return;
    }
    try {
      isVisibleToUser = c.getDeclaredField("mOnScrollChangeListener");
    } catch (Exception e) {
    }
    assert isVisibleToUser == null;

    Albatross.transactionBegin();
    try {
      isVisibleToUser = c.getDeclaredField("mOnScrollChangeListener");
    } catch (Exception e) {
    }
    Albatross.transactionEnd(true);
    assert isVisibleToUser == null;
    Albatross.transactionBegin();
    assert Albatross.addToVisit(c);
    try {
      isVisibleToUser = c.getDeclaredField("mOnScrollChangeListener");
    } catch (Exception e) {
      e.printStackTrace();
    }
    assert isVisibleToUser != null;
    Albatross.transactionEnd(true);
    try {
      isVisibleToUser = c.getDeclaredField("mOnScrollChangeListener");
      isVisibleToUser = null;
    } catch (Exception e) {
    }
    assert isVisibleToUser!=null;


    Albatross.transactionBegin(true);
    try {
      isVisibleToUser = c.getDeclaredField("mOnScrollChangeListener");
      isVisibleToUser = null;
    } catch (Exception e) {
      e.printStackTrace();
    }
    assert isVisibleToUser == null;
    Albatross.transactionEnd(true);
    try {
      isVisibleToUser = c.getDeclaredField("mOnScrollChangeListener");
    } catch (Exception e) {
      e.printStackTrace();
    }
    assert isVisibleToUser != null;
  }
}
