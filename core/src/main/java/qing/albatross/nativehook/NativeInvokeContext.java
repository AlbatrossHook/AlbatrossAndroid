package qing.albatross.nativehook;

public class NativeInvokeContext {

  long mInvokeContext;
  int mFlags;

  NativeInvokeContext(long context, int flags) {
    mInvokeContext = context;
    mFlags = flags;
  }

  public boolean isJavaThread() {
    return (mFlags & 1) == 1;
  }


  public long getNthArgument(int nth) {
    return AlbNative.getNthArgumentNative(mInvokeContext, nth);
  }

  public <T> T getNthArgument(int nth, Class<T> type) {
    long address = AlbNative.getNthArgumentNative(mInvokeContext, nth);
    if (address == 0)
      return null;
    Object obj = AlbNative.readObject(address, type);
    return (T) obj;
  }

  public void setNthArgument(int nth, long value) {
    AlbNative.setNthArgumentNative(mInvokeContext, nth, value);
  }

  public void setResult(long value) {
    AlbNative.setReturnResultNative(mInvokeContext, value);
  }

  public long getResult() {
    return AlbNative.getReturnResultNative(mInvokeContext);
  }

}
