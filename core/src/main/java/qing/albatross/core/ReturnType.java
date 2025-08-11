package qing.albatross.core;

public enum ReturnType {
  VOID(0), BOOL(1), CHAR(2), BYTE(3), SHORT(4), INT(5), FLOAT(6),
  LONG(7), DOUBLE(8), OBJECT(9);

  public final int value;

  ReturnType(int v) {
    this.value = v;
  }
}
