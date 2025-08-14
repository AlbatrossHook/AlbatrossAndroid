package qing.albatross.demo;

import static qing.albatross.demo.TestMain.testGc;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
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

import qing.albatross.annotation.ExecOption;
import qing.albatross.annotation.MethodBackup;
import qing.albatross.core.Albatross;
import qing.albatross.core.InstructionListener;
import qing.albatross.demo.android.ActivityH;
import qing.albatross.demo.android.HandlerHook;
import qing.albatross.demo.server.DemoServer;
import qing.albatross.exception.AlbatrossErr;
import qing.albatross.exception.AlbatrossException;
import qing.albatross.server.JsonFormatter;
import qing.albatross.server.UnixRpcServer;


public class AlbatrossDemoMainActivity extends Activity {

  static boolean isLoad = false;
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

  static void initAlbatross() {
    if (isLoad)
      return;
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

  }

  public void load(View view) {
    if (!isLoad) {
      initAlbatross();
      assert (Albatross.currentApplication() == getApplication());
      return;
    }
    if (!App.test()) {
      throw new RuntimeException("will be caught");
    }
  }


  public void crash(View view) {
    throw new RuntimeException("should call load twice,then exception will be caught");
  }

  public void gcTest(View view) {
    testGc();
  }


  public void compile(View view) throws NoSuchMethodException {
    initAlbatross();
    long isCompile = Albatross.entryPointFromQuickCompiledCode(
        AlbatrossDemoMainActivity.class.getDeclaredMethod(
            "crash", View.class));
    int v = Albatross.compileClass(AlbatrossDemoMainActivity.class, ExecOption.JIT_OPTIMIZED);
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


  public void disableLog(View view) {
    initAlbatross();
    Albatross.disableLog();
    Albatross.log("silence log");
  }

  static int callerCount = 0;


  /**
   * .registers 7
   * <p>
   * .param p1, "view" # Landroid/view/View;
   * <p>
   * .line 180
   * 00003c44: 7100 b700 0000          0000: invoke-static       {}, Lqing/albatross/core/Albatross;->getCallerClass()Ljava/lang/Class; # method@00b7
   * 00003c4a: 0c00                    0003: move-result-object  v0
   * .line 181
   * .local v0, "caller":Ljava/lang/Class;, "Ljava/lang/Class<*>;"
   * 00003c4c: 5451 2d00               0004: iget-object         v1, p0, Lqing/albatross/demo/AlbatrossDemoMainActivity;->textView:Landroid/widget/TextView; # field@002d
   * 00003c50: 2202 3100               0006: new-instance        v2, Ljava/lang/StringBuilder; # type@0031
   * 00003c54: 7010 2600 0200          0008: invoke-direct       {v2}, Ljava/lang/StringBuilder;-><init>()V # method@0026
   * 00003c5a: 1a03 c301               000b: const-string        v3, "caller:" # string@01c3
   * 00003c5e: 6e20 2a00 3200          000d: invoke-virtual      {v2, v3}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder; # method@002a
   * 00003c64: 0c02                    0010: move-result-object  v2
   * 00003c66: 6e10 1800 0000          0011: invoke-virtual      {v0}, Ljava/lang/Class;->getName()Ljava/lang/String; # method@0018
   * 00003c6c: 0c03                    0014: move-result-object  v3
   * 00003c6e: 6e20 2a00 3200          0015: invoke-virtual      {v2, v3}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder; # method@002a
   * 00003c74: 0c02                    0018: move-result-object  v2
   * 00003c76: 1a03 1400               0019: const-string        v3, ":" # string@0014
   * 00003c7a: 6e20 2a00 3200          001b: invoke-virtual      {v2, v3}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder; # method@002a
   * 00003c80: 0c02                    001e: move-result-object  v2
   * 00003c82: 6003 2900               001f: sget                v3, Lqing/albatross/demo/AlbatrossDemoMainActivity;->callerCount:I # field@0029
   * 00003c86: d804 0301               0021: add-int/lit8        v4, v3, 0x1
   * 00003c8a: 6704 2900               0023: sput                v4, Lqing/albatross/demo/AlbatrossDemoMainActivity;->callerCount:I # field@0029
   * 00003c8e: 6e20 2700 3200          0025: invoke-virtual      {v2, v3}, Ljava/lang/StringBuilder;->append(I)Ljava/lang/StringBuilder; # method@0027
   * 00003c94: 0c02                    0028: move-result-object  v2
   * 00003c96: 6e10 2c00 0200          0029: invoke-virtual      {v2}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String; # method@002c
   * 00003c9c: 0c02                    002c: move-result-object  v2
   * 00003c9e: 6e20 0c00 2100          002d: invoke-virtual      {v1, v2}, Landroid/widget/TextView;->setText(Ljava/lang/CharSequence;)V # method@000c
   * .line 182
   * .end local v0 # "caller":Ljava/lang/Class;
   * 00003ca4: 0e00                    0030: return-void
   */


  public void getCaller(View view) {
    initAlbatross();
    Class<?> caller = Albatross.getCallerClass();
    textView.setText("caller:" + caller.getName() + ":" + callerCount++);
  }

  @SuppressLint("BlockedPrivateApi")
  public void exceptionCreate(View view) {
    initAlbatross();
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
    initAlbatross();
    if (Albatross.hookClass(HandlerHook.class) == Albatross.CLASS_ALREADY_HOOK) {
      RuntimeException testHook = new RuntimeException("testHook");
      textView.setText("testHook throw exception：" + testHook);
      throw testHook;
    } else {
      textView.setText("hook HandlerHook");
    }
  }

  public void CompileO(View view) {
    initAlbatross();
    Albatross.setExecConfiguration(ExecOption.JIT_OPTIMIZED, ExecOption.JIT_OPTIMIZED, ExecOption.RECOMPILE_OPTIMIZED);
  }


  public void infer(View view) throws AlbatrossErr {
    initAlbatross();
    ClassInfer.test(true);
  }

  public void field(View view) throws AlbatrossErr {
    initAlbatross();
    FieldTest.test(true);
  }

  public void testMain(View view) throws AlbatrossException {
    initAlbatross();
    Albatross.hookClass(TestMain.TestMainH.class);
    TestMain testMain = new TestMain(2, 2);
    //to short,arm may crash
    testMain.testCall(3);
    assert testMain.z == 5;
  }

  InstructionListener listener = null;

  public void instruction(View view) throws NoSuchMethodException {
    initAlbatross();
    if (listener == null) {
      Method getCaller = AlbatrossDemoMainActivity.class.getDeclaredMethod("getCaller", View.class);
      listener = Albatross.hookInstruction(getCaller, 0, 10, (method, self, dexPc, invocationContext) -> {
        assert dexPc <= 10;
        assert dexPc >= 0;
        assert method == getCaller;
        if (self != AlbatrossDemoMainActivity.this) {
          Albatross.log("self:" + self + " this:" + AlbatrossDemoMainActivity.this);
        }
        assert invocationContext.numberOfVRegs() == 7;
        Albatross.log("onEnter:" + dexPc);
        Object receiver = invocationContext.getParamObject(0);
        assert receiver == self;
        Object v = invocationContext.getParamObject(1);
        assert (v instanceof View);
        if (dexPc == 4) {
//          00003c44: 7100 b700 0000          0000: invoke-static       {}, Lqing/albatross/core/Albatross;->getCallerClass()Ljava/lang/Class; # method@00b7
//          00003c4a: 0c00                    0003: move-result-object  v0
          invocationContext.setVRegObject(0, AlbatrossDemoMainActivity.class);
        }
      });
    } else {
      listener.unHook();
      listener = null;
    }
  }


  InstructionListener onCreate = null;

  public void hookOnCreate(View view) throws NoSuchMethodException {
    initAlbatross();
    if (onCreate == null) {
      Method getCaller = Activity.class.getDeclaredMethod("onCreate", Bundle.class);
      onCreate = Albatross.hookInstruction(getCaller, 0, 100, (method, self, dexPc, invocationContext) -> {
        assert self.getClass().equals(SecondActivity.class);
        Albatross.log("hookOnCreate onEnter:" + dexPc + " this:" + self);
      });
    } else {
      onCreate.unHook();
      onCreate = null;
    }
    startActivity(new Intent(this, SecondActivity.class));
  }


  public void onResume() {
    textView.setText(getApplicationInfo().packageName + ":" + System.currentTimeMillis() + ",testing by continuously clicking the \"load\" button,一定要先注册初始化才能测试");
    super.onResume();
  }


}
