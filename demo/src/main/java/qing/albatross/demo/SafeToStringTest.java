package qing.albatross.demo;

import java.util.Arrays;

import qing.albatross.common.SafeToString;

public class SafeToStringTest {

  public static void test() {
    Object[] array = {
        "正常字符串",
        123,
        new Object(),
        new Object() {
          @Override
          public String toString() {
            throw new RuntimeException("故意抛出的异常");
          }
        }
    };

    String s = SafeToString.arrayToString(array);
    String s1 = SafeToString.safeToString(array);
    assert s.equals(s1);
    try {
      System.out.println("Arrays.toString方法: " + Arrays.toString(array));
      assert false;
    } catch (RuntimeException ignore) {
    }
  }
}
