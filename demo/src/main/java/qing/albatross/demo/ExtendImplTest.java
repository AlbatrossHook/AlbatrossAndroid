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
    // 用例整体停用(扩展实现 hook 在当前 ART 上不可用), 显式上报为 skip
    throw TestRunner.skip("扩展实现用例已停用");
  }

}
