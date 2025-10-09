package qing.albatross.demo;

import android.os.Build;

import qing.albatross.annotation.MethodHook;
import qing.albatross.annotation.TargetClass;
import qing.albatross.core.Albatross;
import qing.albatross.exception.AlbatrossErr;

public class ExtendImplTest {

  interface NumberInterface {
    int calculateNumber();
  }

  static class BaseClass implements NumberInterface {

    public int numberValue;
    public String textValue;

    public String getCombinedValue() {
      return textValue + ":" + numberValue;
    }


    private String generateMessage() {
      return "m:" + getCombinedValue();
    }

    public String generateMessage2() {
      return "m2:" + getCombinedValue();
    }

    public String executeMessageGeneration() {
      return generateMessage();
    }


    @Override
    public int calculateNumber() {
      return numberValue + 1;
    }
  }

  @TargetClass(BaseClass.class)
  static class ExtendedClassA extends BaseClass {

    @MethodHook
    private String generateMessage() {
      return getCombinedValue() + " b：" + textValue;
    }

  }

  @TargetClass(BaseClass.class)
  static abstract class ExtendedClassB implements NumberInterface {

    @MethodHook
    private String generateMessage2() {
      return "m" + calculateNumber();
    }

  }

  public static void test() throws AlbatrossErr {
    if (Build.VERSION.SDK_INT >= 28) {
      assert Albatross.hookClass(ExtendedClassA.class) != 0;
      BaseClass base = new BaseClass();
      base.numberValue = 12;
      base.textValue = "demo";
      String result = base.executeMessageGeneration();
      assert (base.getCombinedValue() + " b：" + base.textValue).equals(result);
      assert Albatross.hookClass(ExtendedClassB.class) != 0;
      result = base.generateMessage2();
      assert ("m" + base.calculateNumber()).equals(result);
    }
  }

}
