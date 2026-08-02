package qing.albatross.nativehook;

public class HookRecord {
  long nativePtr;

  public boolean unHook() {
    if (nativePtr != 0) {
      boolean result = AlbNative.unHookInstructionNative(nativePtr);
      nativePtr = 0;
      return result;
    }
    return false;
  }
}
