package qing.albatross.nativehook;

public class NativeMethodRecord {
  byte[] args;
  byte retType;

  public NativeMethodRecord(byte[] args, byte retType) {
    this.args = args;
    this.retType = retType;
  }
}
