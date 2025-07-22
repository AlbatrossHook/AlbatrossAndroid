package qing.albatross.exception;

import java.lang.reflect.Field;

public class RequiredInstanceErr extends RequiredErr {
  Field field;

  public RequiredInstanceErr(Field field) {
    super("The  type of this field:" + field + " require an object to determine");
    this.field = field;
  }
}
