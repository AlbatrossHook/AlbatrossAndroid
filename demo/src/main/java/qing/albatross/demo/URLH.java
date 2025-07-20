package qing.albatross.demo;

import java.io.IOException;
import java.net.Proxy;
import java.net.URL;
import java.net.URLConnection;

import qing.albatross.annotation.MethodBackup;
import qing.albatross.annotation.MethodHook;
import qing.albatross.annotation.TargetClass;
import qing.albatross.core.Albatross;


@TargetClass(URL.class)
public class URLH {


  static String getExceptionDesc(Exception e) {
    Albatross.log(e.toString(), e);
    return "desc:" + e.toString();
//    Albatross.log("getExceptionDesc", e);
//    return "demo";
  }
  static String getExceptionDesc() {
//    Albatross.log(e.toString(), e);
//    return "desc:" + e.toString();
    Albatross.log("getExceptionDesc", new Exception("openConnection"));
    return "demo";
  }

//  @MethodBackup
//  static native URLConnection openConnection(URL url);
  @MethodBackup
  @MethodHook//(compileHooker = CompileOption.COMPILE_DECOMPILE)
  private static URLConnection openConnection(URL url) throws IOException {
    getExceptionDesc(new Exception("openConnectionStatic"));
    return openConnection(url);
  }


  @MethodBackup
  @MethodHook
  static URLConnection openConnection(URL url, Proxy proxy) throws IOException {
    getExceptionDesc(new Exception("openConnection2"));
    return openConnection(url, proxy);
  }
}
