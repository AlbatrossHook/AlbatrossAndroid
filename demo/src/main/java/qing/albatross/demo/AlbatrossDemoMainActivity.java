package qing.albatross.demo;

import static qing.albatross.demo.TestMain.testGc;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.os.Bundle;
import android.os.Debug;
import android.view.View;
import android.widget.TextView;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Method;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

import qing.albatross.annotation.CompileOption;
import qing.albatross.annotation.MethodBackup;
import qing.albatross.core.Albatross;
import qing.albatross.demo.android.ActivityH;
import qing.albatross.demo.android.HandlerHook;
import qing.albatross.demo.server.DemoServer;
import qing.albatross.exception.AlbatrossErr;
import qing.albatross.exception.AlbatrossException;
import qing.albatross.server.JsonFormatter;
import qing.albatross.server.UnixRpcServer;


public class AlbatrossDemoMainActivity extends Activity {
  public static class A {
    public String isFinishing0() {
      return "0";
    }

    public String isFinishing() {
      return "1";
    }

    public String isFinishing2() {
      return "2";
    }
  }

  public static class AH {
    @MethodBackup
    private native String isFinishing();
  }


  protected boolean isLoad = false;

  protected DemoServer demoServer;
  protected TextView textView;


  public void fixLayout() {
    setContentView(R.layout.activity_albatross_demo_main);
    textView = findViewById(R.id.sample_text);
    textView.setText(getPackageName());
  }

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    fixLayout();
  }

  private static native boolean registerAlbatrossLib(Class<?> albatross);


  public void load(View view) {
    if (!isLoad) {
      isLoad = true;
      try {
        System.loadLibrary("api");
        registerAlbatrossLib(Albatross.class);
        Albatross.init(0);
      } catch (Throwable ignore) {
        Albatross.loadLibrary("albatross_base");
      }
      boolean res = Albatross.initRpcClass(UnixRpcServer.class);
      assert res;
      assert (Albatross.currentApplication() == getApplication());
      return;
    }
    if (!App.test()) {
      throw new RuntimeException("will be caught");
    }
  }

  public void server(View view) {
    String socketPath = "albatross_demo" + Albatross.getRuntimeISA();
    if (demoServer == null) {
      demoServer = new DemoServer();
      assert demoServer.createServer(socketPath, true) != null;
    }
    textView.setText("localabstract:" + socketPath);
  }

  public void broadcast(View view) {
    if (demoServer == null)
      server(view);
    String result = demoServer.broadcast("hello");
    int sendPrimary = demoServer.sendPrimary("primary", 3);
    Map<String, Object> map = new HashMap<>();
    map.put("key", 1);
    map.put("string", "s value");
    short sh = demoServer.jsonObject(JsonFormatter.fmt(map), JsonFormatter.fmt(new Object[]{"one", true, 3}));
    textView.setText("broadcast result:" + result + "\n sendPrimary:" + sendPrimary + "\njsonObject:" + sh);
  }

  public void broadcastLong(View view) {
    if (demoServer == null)
      server(view);
    byte result = demoServer.broadcastLongArgTest(1.3, "2hello", (byte) 3, 4, 5.2, (byte) 6, "7arg", 8, 9, (byte) 10, 11.2f);
    textView.setText("broadcast result:" + result);
  }

  public void broadcastStr(View view) {
    if (demoServer == null)
      server(view);
    byte result = demoServer.sendLongString("{\"permissions\":[\"android.permission.ACCESS_FINE_LOCATION\",\"android.permission.ACCESS_COARSE_LOCATION\",\"android.permission.ACCESS_LOCATION_EXTRA_COMMANDS\"],\"requestId\":-2352445327731730071,\"finish\":false,\"list\":[{\"affectedPermissions\":[\"android.permission.ACCESS_FINE_LOCATION\",\"android.permission.ACCESS_COARSE_LOCATION\"],\"name\":\"android.permission-group.LOCATION\",\"state\":0,\"desc\":\"access this device\\'s location\"}],\"pkg\":\"kaamel.kabox.android.uidemo64\",\"aid\":234938715}");
    textView.setText("sendLongString:" + result);
  }

  public void broadcastVoid(View view) {
    if (demoServer == null)
      server(view);
    demoServer.sendNoReturn(12345678902545L);
    textView.setText("broadcastVoid,subscriberSize:" + demoServer.getSubscriberSize());
  }

  public void crash(View view) {
    throw new RuntimeException("exception will be caught");
  }

  public void gcTest(View view) {
    testGc();
  }


  public void compile(View view) throws NoSuchMethodException {
    long isCompile = Albatross.entryPointFromQuickCompiledCode(
        AlbatrossDemoMainActivity.class.getDeclaredMethod(
            "crash", View.class));
    int v = Albatross.compileClass(AlbatrossDemoMainActivity.class, CompileOption.COMPILE_OPTIMIZED);
    textView.setText(
        "compile:" + v + " isCompile:" + isCompile + " field:" + !Albatross.containsFlags(
            Albatross.FLAG_FIELD_INVALID
        ));
  }

  public void debug(View view) {
    if (Debug.isDebuggerConnected()) {
      textView.setText("debugger");
    } else {
      textView.setText("no debugger");
    }
  }

  public void virtualCall(View view) throws AlbatrossErr {
    Albatross.hookClass(AH.class, A.class);
    AH self = Albatross.convert(new A(), AH.class);
    Albatross.hookClass(ActivityH.class);
    ActivityH activityH = Albatross.convert(this, ActivityH.class);
    textView.setText("isFinished：" + self.isFinishing() + ":" + ActivityH.finish(activityH));
  }

  public void disableLog(View view) {
    Albatross.disableLog();
    Albatross.log("silence log");
  }

  static int callerCount = 0;

  public void getCaller(View view) {
    Class<?> caller = Albatross.getCallerClass();
    textView.setText("caller:" + caller.getName() + ":" + callerCount++);
  }

  @SuppressLint("BlockedPrivateApi")
  public void exceptionCreate(View view) {
    try {
      Method method = StringBuilder.class.getDeclaredMethod("append", String.class);
      textView.setText("append:" + Albatross.entryPointFromQuickCompiledCode(method));
      if (Albatross.hookClass(URLH.class) == Albatross.CLASS_ALREADY_HOOK) {
        new Thread(new Runnable() {
          @Override
          public void run() {
            HttpURLConnection connection = null;
            try {
              URL url = new URL("https://www.baidu.com");
              connection = (HttpURLConnection) url.openConnection();
              connection.setRequestMethod("GET");
              InputStream in = connection.getInputStream();
              BufferedReader bufr = new BufferedReader(new InputStreamReader(in));
              StringBuilder response = new StringBuilder();
              String line;
              while ((line = bufr.readLine()) != null) {
                response.append(line);
              }
              Albatross.log("connect:" + line);
            } catch (Exception e) {
              e.printStackTrace();
            } finally {
              if (connection != null) {
                connection.disconnect();
              }
            }
          }
        }).start();
      }
    } catch (AlbatrossErr | NoSuchMethodException e) {
      throw new RuntimeException(e);
    }
  }

  public void handlerHook(View view) throws AlbatrossErr {
    if (Albatross.hookClass(HandlerHook.class) == Albatross.CLASS_ALREADY_HOOK) {
      RuntimeException testHook = new RuntimeException("testHook");
      textView.setText("testHook throw exception：" + testHook);
      throw testHook;
    } else {
      textView.setText("hook HandlerHook");
    }
  }

  public void CompileO(View view) {
    Albatross.setCompileConfiguration(CompileOption.COMPILE_OPTIMIZED, CompileOption.COMPILE_OPTIMIZED, CompileOption.COMPILE_OPTIMIZED_JIT);
  }

  public void infer(View view) throws AlbatrossErr {
    ClassInfer.test(true);
  }

  public void field(View view) throws AlbatrossErr {
    FieldTest.test(true);
  }

  public void testMain(View view) throws AlbatrossException, NoSuchMethodException {
    Albatross.hookClass(TestMain.TestMainH.class);
    TestMain testMain = new TestMain(2, 2);
    testMain.testCall(3);
  }


  public void onResume() {
    textView.setText(getApplicationInfo().packageName + ":" + System.currentTimeMillis());
    super.onResume();
  }


}
