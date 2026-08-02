package qing.albatross.nativehook;

public interface InstructionCallback {

  void onCall(NativeInvokeContext invokeContext,Object userdata);
}
