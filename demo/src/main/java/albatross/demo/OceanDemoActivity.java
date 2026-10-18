package albatross.demo;

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.webkit.CookieManager;
import android.widget.TextView;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

import qing.albatross.annotation.ExecutionMode;
import qing.albatross.annotation.ExecutionOption;
import qing.albatross.core.Albatross;
import qing.albatross.demo.R;
import qing.albatross.tracker.DefaultSinker;
import qing.albatross.tracker.EventType;
import qing.albatross.tracker.OceanLogger;
import qing.albatross.tracker.OceanTracker;
import qing.albatross.tracker.StringSinker;

public class OceanDemoActivity extends Activity {
  TextView textView;


  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_ocean);
    textView = findViewById(R.id.sample_text);
    OceanLogger.registerLogger();
  }


  static class MySinker extends DefaultSinker {
    static int count = 0;
    public int id;

    public MySinker(boolean mainThread) {
      super(mainThread);
      id = count++;
      Albatross.log("Sinker " + id + " create");
    }

  }

  static boolean isInit = false;

  public void load(View view) throws NoSuchMethodException {
    if (!isInit) {
      assert OceanTracker.init(0);
      isInit = true;
      return;
    }
    assert !OceanTracker.isCurrentThreadTraced();
    String s = Albatross.methodToString(OceanDemoActivity.class.getDeclaredMethod("load", View.class));
    Albatross.log("method:" + s);
    s = Albatross.methodToString(View.class.getDeclaredConstructor(Context.class));
    Albatross.log("constructor:" + s);
    OceanTracker.setTraceScope(OceanTracker.kApplication | OceanTracker.kPackageFilter);
    OceanTracker.syncTrace();
    trace(view);
    traceCall(view);

    traceGlobalCall(view);
    testMulThreadTrace(view);
    finishTrace(view);

    traceGlobalMain(view);

    threadTracker(view);
    threadCall(view);

    traceString(view);

    traceStringGlobal(view);
  }

  public void traceCookie(View view) {
    OceanTracker.init(0);
    CookieManager cookieManager = CookieManager.getInstance();
    MySinker sinker = new MySinker(false);
    OceanTracker.traceMe(sinker, EventType.RETURN);
    cookieManager.getCookie("baidu.com");
    OceanTracker.stopTraceMe();
    assert sinker.callCount > 0;
  }

  @ExecutionMode(ExecutionOption.INTERPRETER)
  public void trace(View view) {
    OceanTracker.init(0);
    MySinker sinker = new MySinker(false);
    assert OceanTracker.traceMe(sinker, EventType.ALL);
    MySinker sinker1 = new MySinker(false);
    assert !OceanTracker.traceMe(sinker1, EventType.ALL);
    textView.setText("trace start");
    testS("trace view");
    assert OceanTracker.stopTraceMe();
    assert !OceanTracker.stopTraceMe();
    assert sinker1.callCount == 0;
    assert sinker.callCount > 0;
  }

  static class A {
    double i;

    public A(double i) {
      this.i = i;
      Albatross.log("A:" + i);
    }
  }

  @ExecutionMode(ExecutionOption.INTERPRETER)
  public void traceCall(View view) {
    OceanTracker.init(0);
    MySinker sinker = new MySinker(false);
    assert OceanTracker.traceMe(sinker, EventType.CALL);
    assert !OceanTracker.traceMe(new MySinker(false), EventType.ALL);
    new A(1.2);
    textView.setText("trace call start");
    assert OceanTracker.stopTraceMe();
    assert !OceanTracker.stopTraceMe();
    assert sinker.callCount > 0;
  }

  @ExecutionMode(ExecutionOption.INTERPRETER)
  public void traceGlobalCall(View view) {
    OceanTracker.init(0);
    assert OceanTracker.traceGlobal(new MySinker(false), EventType.CALL);
    assert !OceanTracker.traceGlobal(new MySinker(false), EventType.ALL);
    textView.setText("trace call global start");
    new Thread() {
      @Override
      public void run() {
        textView.post(() -> {
          textView.setText("trace call global thread");
        });
      }
    }.start();
  }


  public void finishTrace(View view) {
    Albatross.log("traceGlobalCall");
    assert OceanTracker.stopTraceGlobal();
    assert !OceanTracker.stopTraceGlobal();
  }


  @ExecutionMode(ExecutionOption.INTERPRETER)
  public void traceGlobalMain(View view) {
    OceanTracker.init(0);
    assert OceanTracker.traceGlobal(new MySinker(true), EventType.CALL);
    assert !OceanTracker.traceGlobal(new MySinker(true), EventType.ALL);
    textView.setText("trace call global start");
    Thread thread = new Thread() {
      @Override
      public void run() {
        try {
          Thread.sleep(1000);
        } catch (InterruptedException ignore) {
        }
        textView.post(() -> {
          textView.setText("trace call global stop");
        });
        Albatross.log("traceGlobalCall");
        assert OceanTracker.stopTraceGlobal();
        assert !OceanTracker.stopTraceGlobal();
      }
    };
    thread.start();
    try {
      thread.join();
    } catch (InterruptedException ignore) {
    }
    assert !OceanTracker.stopTraceGlobal();
    assert !OceanTracker.isCurrentThreadTraced();
  }


  public void threadTracker(View view) {
    OceanTracker.init(0);
    new Thread() {
      @Override
      public void run() {
        OceanTracker.traceMe(new MySinker(false), EventType.ALL);
        System.out.println("thread tracker demo");
        Albatross.log("threadTracker");
      }
    }.start();
  }

  public void threadCall(View view) {
    OceanTracker.init(0);
    new Thread() {
      @Override
      public void run() {
        MySinker sinker = new MySinker(false);
        OceanTracker.traceMe(sinker, EventType.CALL);
        Albatross.log("threadTracker");
        testS("hello");
        assert sinker.callCount > 0;
      }
    }.start();
  }

  public void dumpMethod(View view) {
    OceanTracker.setDumpConfig(getFilesDir().getAbsolutePath());
    Constructor<?>[] constructors = Activity.class.getDeclaredConstructors();
    for (Constructor<?> constructor : constructors) {
      OceanTracker.dumpMethod(constructor);
    }
    Method[] methods = OceanDemoActivity.class.getDeclaredMethods();
    for (Method method : methods) {
      OceanTracker.dumpMethod(method);
    }
  }

  public void finishDump(View view) {
    OceanTracker.finishDump();
  }

  public void unShell(View view) {
    OceanTracker.init(0);
    OceanTracker.setDumpConfig(Albatross.currentApplication().getCacheDir().getAbsolutePath());
    OceanTracker.traceGlobal(new MySinker(false), EventType.DUMP);
  }

  public void finishUnshell(View view) {
    boolean result = OceanTracker.stopTraceGlobal();
    Albatross.getMainHandler().postDelayed(() -> {
      OceanTracker.finishDump();
      textView.setText("finish unshell:" + result);
    }, 1000);
  }

  public void getTraceCount(View view) {
    textView.setText("count:" + OceanTracker.getTraceCount());
  }

  public void testMulThreadTrace(View view) {
    for (int i = 0; i < 10; i++) {
      int finalI = i;
      new Thread() {
        @Override
        public void run() {
          Albatross.log("demo" + finalI);
        }
      }.start();
    }
  }


  public void threadSleep(View view) {
    for (int i = 0; i < 10; i++) {
      int finalI = i;
      new Thread() {
        @Override
        public void run() {
          try {
            Thread.sleep(1000 * 30 + finalI * 10 * 1000);
          } catch (InterruptedException e) {
          }
          Albatross.log("demo" + finalI);
        }
      }.start();
    }
  }

  public void addWhite(View view) {
    assert OceanTracker.addWhitePackage("qing.albatross");
    assert !OceanTracker.addWhitePackage("qing.albatross");
    assert !OceanTracker.addWhitePackage("qing.albatross.demo");
  }

  public void removeWhite(View view) {
    assert !OceanTracker.removeWhitePackage("qing.albatross.demo");
    assert OceanTracker.removeWhitePackage("qing.albatross");
    assert !OceanTracker.removeWhitePackage("qing.albatross");
  }

  public String testS(String a) {
    return "a:" + a;
  }

  public int testI(int a, String s) {
    getPackageManager();
    return 100 * a + s.length();
  }


  public void traceString(View view) {
    OceanTracker.syncTrace();
    StringSinker qing = new StringSinker("qing");
    OceanTracker.traceMe(qing, EventType.CALL);
    testS("qing_b");
    testS("zi_b");
    testS("qing");
    OceanTracker.stopTraceMe();
    assert qing.callCount > 0;
  }

  public void traceReturn(View view) {
    MySinker sinker = new MySinker(true);
    OceanTracker.traceMe(sinker, EventType.RETURN);
    testS("qing_b");
    testI(10, "zi_b");
    testS("qing");
    textView.setText("traceReturn test");
    OceanTracker.stopTraceMe();
    assert sinker.callCount > 0;
  }

  @ExecutionMode(ExecutionOption.INTERPRETER)
  public void returnGlobal(View view) {
    OceanTracker.init(0);
    MySinker sinker = new MySinker(false);
    assert OceanTracker.traceGlobal(sinker, EventType.RETURN);
    assert !OceanTracker.traceGlobal(new MySinker(false), EventType.RETURN);
    textView.setText("trace call global return start");
    new Thread() {
      @Override
      public void run() {
        testS("thread qing_b");
        testI(100, "thread zi_b");
        testS("thread qing");
        textView.post(() -> {
          textView.setText("trace call global thread");
          assert OceanTracker.stopTraceGlobal();
        });
      }
    }.start();
    testS("main qing_b");
    testI(10, "main zi_b");
    testS("main qing");
    assert sinker.callCount > 2;
  }

  public void traceStringGlobal(View view) {
    OceanTracker.syncTrace();
    StringSinker qing = new StringSinker("qing");
    OceanTracker.syncTrace();
    boolean res = OceanTracker.traceGlobal(qing, EventType.CALL);
    assert res;
    new Thread() {
      @Override
      public void run() {
        testS("qing_b_thread");
        boolean isTraced = OceanTracker.isCurrentThreadTraced();
        if (!isTraced) {
          Albatross.log("why not traced");
        }
        testS("zi_b");
        testS("qing_thread");
        synchronized (qing) {
          qing.notify();
        }
      }
    }.start();
    synchronized (qing) {
      try {
        qing.wait(300000);
      } catch (InterruptedException e) {
        Albatross.log("traceStringGlobal timeout");
      }
    }
    OceanTracker.stopTraceGlobal();
    assert qing.callCount > 0;
  }

  public void traceEntry(View view) {
    OceanTracker.setTraceScope(OceanTracker.kPlatform | OceanTracker.kEntryOnly);
  }

  public void traceThreadReturn(View view) {
    if (!isInit) {
      isInit = true;
      assert OceanTracker.init(0);
    }
    OceanTracker.setTraceScope(OceanTracker.kApplication | OceanTracker.kEntryOnly | OceanTracker.kPackageFilter);
    new Thread(() -> {
      OceanTracker.syncTrace();
      MySinker sinker = new MySinker(false);
      OceanTracker.traceMe(sinker, EventType.RETURN | EventType.CALL_ENTER);
      getApplication();
      testI(1, "123");
      testI(1, "123");
      getWindowManager();
//      traceEntry(view);
      assert sinker.callCount > 0;
    }).start();
  }


}
