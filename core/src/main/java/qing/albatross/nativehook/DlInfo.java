package qing.albatross.nativehook;

import static qing.albatross.nativehook.AlbNative.dlclose;
import static qing.albatross.nativehook.AlbNative.dlsym;

import java.lang.reflect.Method;

import qing.albatross.core.Albatross;

public class DlInfo {
  private long handle;

  public DlInfo(long handle) {
    this.handle = handle;
  }

  public long enumerateFunctions(SearchCallback callback) {
    return AlbNative.iterSymbol(handle, callback);
  }


  public long getSymbolAddress(String symbol) {
    if (handle > 4096 || handle < 0) {
      long symbolAddr = dlsym(handle, symbol);
      if (symbolAddr == 0)
        Albatross.log("can not get symbol " + symbol);
      return symbolAddr;
    }
    return 0;
  }

  public void close() {
    if (handle > 4096 || handle < 0) {
      dlclose(handle);
      handle = 0;
    }
  }

  public void backup(long symbol, Method method) {

  }


}