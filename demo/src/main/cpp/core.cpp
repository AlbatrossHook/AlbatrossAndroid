#include <jni.h>
#include "albatross.h"
#include <pthread.h>
#include <unistd.h>
#include <fcntl.h>
#include <stdio.h>
#include <stdint.h>
#include <string.h>
#include <errno.h>
#include <dlfcn.h>
#include <android/log.h>

extern "C" jint JNICALL JNI_OnLoad(JavaVM *vm, void *reserved) {
    return JNI_VERSION_1_6;
}


extern "C"
JNIEXPORT  JNICALL jstring
Java_qing_albatross_demo_AlbatrossDemoMainActivity_registerAlbatrossLib(JNIEnv *env, jclass clazz,
                                                                        jclass albatross,
                                                                        int version) {
    int current_version = AlbatrossGetVersion();
    if (current_version != version) {
        return env->NewStringUTF("native库过时，请从github下载最新的so库");
    }
    int err_code = AlbatrossAndroidInit(env, albatross);
    if (err_code) {
        return env->NewStringUTF("初始化失败，请拉取最新的代码");
    }
    return nullptr;
}

struct Args {
    jint i1;
    jbyte b2;
    jlong l3;
};

extern "C"
JNIEXPORT jint JNICALL
Java_qing_albatross_demo_NativeInstructionHookTest_getInt(JNIEnv *env, jclass clazz, jint i1,
                                                          jbyte b2, jlong l3) {
    return i1 * 10 + b2;
}

static void *native_thread(void *d) {
    struct Args *args = (struct Args *) d;
    jint result = Java_qing_albatross_demo_NativeInstructionHookTest_getInt(0, 0, args->i1, args->b2,
                                                                            args->l3);
    return (void *) result;
}
extern "C"
JNIEXPORT jint JNICALL
Java_qing_albatross_demo_NativeInstructionHookTest_getIntNativeThread(JNIEnv *env, jclass clazz,
                                                                      jint i1, jbyte b2, jlong l3) {
    pthread_t t;
    struct Args args = {i1, b2, l3};
    pthread_create(&t, 0, native_thread, &args);
    void *result;
    pthread_join(t, &result);
    return (jint) (long) result;
}

// ---- NativeHookTest: C 层目标函数 + JNI 直调包装 ----
// 目标函数保持 C 链接与非 static: FuncHook 按 libapi.so 的符号名找入口。

extern "C" {

int getInt(int v) {
  printf("enlarge function body");
  return v + 100;
}

static int64_t g_addlong_args[2];

int64_t addLong(int64_t a, int64_t b) {
  g_addlong_args[0] = a;
  g_addlong_args[1] = b;
  return a * 1000000L + b;
}

static char greet_buf[128];

const char *greet(const char *name) {
  snprintf(greet_buf, sizeof(greet_buf), "hi %s", name);
  return greet_buf;
}

static int g_sum10_args[10];

int sum10(int a1, int a2, int a3, int a4, int a5, int a6, int a7, int a8, int a9, int a10) {
  g_sum10_args[0] = a1;
  g_sum10_args[1] = a2;
  g_sum10_args[2] = a3;
  g_sum10_args[3] = a4;
  g_sum10_args[4] = a5;
  g_sum10_args[5] = a6;
  g_sum10_args[6] = a7;
  g_sum10_args[7] = a8;
  g_sum10_args[8] = a9;
  g_sum10_args[9] = a10;
  return a1 + a2 + a3 + a4 + a5 + a6 + a7 + a8 + a9 + a10;
}

static int g_counter = 0;

void bump(int delta) {
  g_counter += delta;
}

int getCounter() {
  return g_counter;
}

int twice(int v) {
  return v * 2;
}

} // extern "C"

extern "C" JNIEXPORT jint JNICALL
Java_qing_albatross_demo_NativeHookTest_getInt(JNIEnv *env, jclass clazz, jint v) {
  (void) env;
  (void) clazz;
  return getInt(v);
}

extern "C" JNIEXPORT jlong JNICALL
Java_qing_albatross_demo_NativeHookTest_getAddLongArg(JNIEnv *env, jclass clazz, jint idx) {
  (void) env;
  (void) clazz;
  if (idx < 0 || idx > 1)
    return -1;
  return g_addlong_args[idx];
}

extern "C" JNIEXPORT jlong JNICALL
Java_qing_albatross_demo_NativeHookTest_addLong(JNIEnv *env, jclass clazz, jlong a, jlong b) {
  (void) env;
  (void) clazz;
  return addLong(a, b);
}

extern "C" JNIEXPORT jstring JNICALL
Java_qing_albatross_demo_NativeHookTest_greet(JNIEnv *env, jclass clazz, jstring name) {
  (void) clazz;
  const char *n = env->GetStringUTFChars(name, nullptr);
  const char *r = greet(n);
  env->ReleaseStringUTFChars(name, n);
  return env->NewStringUTF(r);
}

extern "C" JNIEXPORT jint JNICALL
Java_qing_albatross_demo_NativeHookTest_sum10(JNIEnv *env, jclass clazz, jint a1, jint a2,
                                              jint a3, jint a4, jint a5, jint a6, jint a7,
                                              jint a8, jint a9, jint a10) {
  (void) env;
  (void) clazz;
  return sum10(a1, a2, a3, a4, a5, a6, a7, a8, a9, a10);
}

extern "C" JNIEXPORT jint JNICALL
Java_qing_albatross_demo_NativeHookTest_getSum10Arg(JNIEnv *env, jclass clazz, jint idx) {
  (void) env;
  (void) clazz;
  if (idx < 0 || idx >= 10)
    return -1;
  return g_sum10_args[idx];
}

extern "C" JNIEXPORT void JNICALL
Java_qing_albatross_demo_NativeHookTest_bump(JNIEnv *env, jclass clazz, jint delta) {
  (void) env;
  (void) clazz;
  bump(delta);
}

extern "C" JNIEXPORT jint JNICALL
Java_qing_albatross_demo_NativeHookTest_getCounter(JNIEnv *env, jclass clazz) {
  (void) env;
  (void) clazz;
  return getCounter();
}

extern "C" JNIEXPORT jint JNICALL
Java_qing_albatross_demo_NativeHookTest_twice(JNIEnv *env, jclass clazz, jint v) {
  (void) env;
  (void) clazz;
  return twice(v);
}

extern "C" JNIEXPORT jstring JNICALL
Java_qing_albatross_demo_NativeInstructionHookTest_getString(JNIEnv *env, jclass clazz, jstring a) {
  (void) clazz;
  const char *in = env->GetStringUTFChars(a, nullptr);
  char temp[128];
  snprintf(temp, sizeof(temp), "%s:test", in);
  env->ReleaseStringUTFChars(a, in);
  return env->NewStringUTF(temp);
}

// ---- NativeBackupTest 对照层: 纯 JNI 直调(不经 backup) ----

extern "C" JNIEXPORT void JNICALL
Java_qing_albatross_demo_NativeBackupTest_writeTest(JNIEnv *env, jclass clazz, jstring path) {
  (void) clazz;
  const char *s = env->GetStringUTFChars(path, nullptr);
  int fd = open(s, O_CREAT | O_TRUNC | O_WRONLY, 0644);
  if (fd > 0) {
    close(fd);
  } else {
    printf("err :%d\n", errno);
  }
  env->ReleaseStringUTFChars(path, s);
}

extern "C" JNIEXPORT jstring JNICALL
Java_qing_albatross_demo_NativeBackupTest_roundTrip(JNIEnv *env, jclass clazz,
                                                    jstring path, jstring content) {
  (void) clazz;
  const char *p = env->GetStringUTFChars(path, nullptr);
  const char *c = env->GetStringUTFChars(content, nullptr);
  size_t len = strlen(c);
  char buf[512];
  if (len >= sizeof(buf)) {
    env->ReleaseStringUTFChars(path, p);
    env->ReleaseStringUTFChars(content, c);
    return env->NewStringUTF("");
  }
  int fd = open(p, O_CREAT | O_TRUNC | O_WRONLY, 0644);
  if (fd < 0) {
    env->ReleaseStringUTFChars(path, p);
    env->ReleaseStringUTFChars(content, c);
    return nullptr;
  }
  ssize_t w = write(fd, c, len);
  close(fd);
  fd = open(p, O_RDONLY, 0);
  if (fd < 0) {
    env->ReleaseStringUTFChars(path, p);
    env->ReleaseStringUTFChars(content, c);
    return nullptr;
  }
  ssize_t r = read(fd, buf, sizeof(buf) - 1);
  close(fd);
  env->ReleaseStringUTFChars(path, p);
  env->ReleaseStringUTFChars(content, c);
  if (w != (ssize_t) len || r != w) {
    return env->NewStringUTF("");
  }
  buf[r] = '\0';
  return env->NewStringUTF(buf);
}

extern "C" JNIEXPORT jint JNICALL
Java_qing_albatross_demo_NativeBackupTest_absDirect(JNIEnv *env, jclass clazz, jint v) {
  (void) env;
  (void) clazz;
  return v < 0 ? -v : v;
}

extern "C" JNIEXPORT jlong JNICALL
Java_qing_albatross_demo_NativeBackupTest_strlenDirect(JNIEnv *env, jclass clazz, jstring s) {
  (void) clazz;
  const char *c = env->GetStringUTFChars(s, nullptr);
  jlong r = (jlong) strlen(c);
  void *sym = dlsym(RTLD_DEFAULT, "strlen");
  env->ReleaseStringUTFChars(s, c);
  __android_log_print(ANDROID_LOG_INFO, "albatross",
                      "DIAG strlenDirect ret=%lld dlsym=%p", (long long) r, sym);
  return r;
}

// ---- CModuleTest: 供 CModule 编译出的 C 代码 hook/调用的目标 ----

// noinline: 防止被 JNI 包装内联后 hook 点消失
__attribute__((noinline)) int cmodule_target_double(int v) {
  return v * 2;
}

__attribute__((noinline)) int cmodule_injected_mul(int a, int b) {
  return a * b;
}

// ---- 追踪窗口用例(StalkerTimeoutTest): 长跑目标 ----
// 循环足够长: 插桩执行下远超超时窗口, 超时/上限一定在函数执行期间触发

__attribute__((noinline)) static int stalker_long_helper(int x) { return x + 1; }

__attribute__((noinline)) static int stalker_long_impl(int loops) {
  int sum = 0;
  for (int i = 0; i < loops; i++)
    sum += stalker_long_helper(i);
  return sum;
}

extern "C"
JNIEXPORT jint JNICALL
Java_qing_albatross_demo_CModuleTest_nativeTarget(JNIEnv *env, jclass clazz, jint v) {
  (void) env;
  (void) clazz;
  return cmodule_target_double(v);
}
extern "C"
JNIEXPORT jlong JNICALL
Java_qing_albatross_demo_CModuleTest_nativeTargetAddress(JNIEnv *env, jclass clazz) {
  (void) env;
  (void) clazz;
  return (jlong) (uintptr_t) cmodule_target_double;
}
extern "C"
JNIEXPORT jlong JNICALL
Java_qing_albatross_demo_CModuleTest_injectedMulAddress(JNIEnv *env, jclass clazz) {
  (void) env;
  (void) clazz;
  return (jlong) (uintptr_t) cmodule_injected_mul;
}

// ---- StaticBackupRecursionTest: 挡住类可见化 checkpoint 的 native 自旋 ----
// 类初始化结束时 ART 用 RunCheckpoint 把 kInitialized 推到 kVisiblyInitialized，并
// 在最后一步跑 FixupStaticTrampolines。处于 JNI native 态的线程不执行 checkpoint，
// 所以这个自旋会把"已初始化但还没可见化"的窗口撑住，供 hook 安装在窗口内发生。
extern "C"
JNIEXPORT void JNICALL
Java_qing_albatross_demo_StaticBackupRecursionTest_spinNative(JNIEnv *env, jclass clazz, jint ms) {
  (void) env;
  (void) clazz;
  struct timespec start, now;
  clock_gettime(CLOCK_MONOTONIC, &start);
  do {
    clock_gettime(CLOCK_MONOTONIC, &now);
  } while ((now.tv_sec - start.tv_sec) * 1000L + (now.tv_nsec - start.tv_nsec) / 1000000L < ms);
}


// ---- 跨线程 follow 用例: 纯 native 线程跑目标函数 ----
// 目标线程用纯自旋同步而非 futex: 线程会被 ptrace 劫持注入 infect thunk,
// 挂在 futex 上时劫持后恢复容易死锁, 自旋则可安全恢复

static pthread_t stalker_target_thread;
static volatile pid_t stalker_target_tid;
static volatile int stalker_target_go;
static volatile int stalker_target_result;



// ---- StalkerTest 追踪目标: 三级纯 C 调用链 ----

static int stalker_inner(int n) { return n * 10 + 1; }

static int stalker_mid(int n) { return stalker_inner(n) + stalker_inner(n + 1); }

static int stalker_target_impl(int n) {
  int a = stalker_mid(n);
  int b = stalker_inner(n - 1);
  return a - b;
}



static void *stalker_thread_main(void *arg) {
  (void) arg;
  stalker_target_tid = gettid();
  __sync_synchronize();
  while (!stalker_target_go)
    sched_yield();
  __sync_synchronize();
  stalker_target_result = stalker_target_impl(5);
  __sync_synchronize();
  return NULL;
}
extern "C"
JNIEXPORT jlong JNICALL
Java_qing_albatross_demo_StalkerTest_startTargetThread(JNIEnv *env, jclass clazz) {
  (void) env;
  (void) clazz;
  stalker_target_tid = 0;
  stalker_target_go = 0;
  stalker_target_result = 0;
  pthread_create(&stalker_target_thread, NULL, stalker_thread_main, NULL);
  while (stalker_target_tid == 0)
    sched_yield();
  return (jlong) stalker_target_tid;
}

extern "C"
JNIEXPORT jint JNICALL
Java_qing_albatross_demo_StalkerTest_targetFunction(JNIEnv *env, jclass clazz, jint n) {
  return stalker_target_impl(n);
}

extern "C"
JNIEXPORT jlong JNICALL
Java_qing_albatross_demo_StalkerTest_targetAddress(JNIEnv *env, jclass clazz) {
  // clear thumb bit on arm32
  return (jlong) ((uintptr_t) &stalker_target_impl & ~(uintptr_t) 1);
}

extern "C"
JNIEXPORT jlong JNICALL
Java_qing_albatross_demo_StalkerTest_innerAddress(JNIEnv *env, jclass clazz) {
  return (jlong) ((uintptr_t) &stalker_inner & ~(uintptr_t) 1);
}

// ---- StalkerTest 用例 6: 递归目标函数(验证深度计数不破坏递归) ----

static int stalker_fib(int n) {
  if (n < 2)
    return n;
  return stalker_fib(n - 1) + stalker_fib(n - 2);
}


extern "C"
JNIEXPORT jint JNICALL
Java_qing_albatross_demo_StalkerTest_fib(JNIEnv *env, jclass clazz, jint n) {
  return stalker_fib(n);
}

extern "C"
JNIEXPORT jlong JNICALL
Java_qing_albatross_demo_StalkerTest_fibAddress(JNIEnv *env, jclass clazz) {
  return (jlong) ((uintptr_t) &stalker_fib & ~(uintptr_t) 1);
}

extern "C"
JNIEXPORT void JNICALL
Java_qing_albatross_demo_StalkerTest_releaseTargetThread(JNIEnv *env, jclass clazz) {
  (void) env;
  (void) clazz;
  stalker_target_go = 1;
  __sync_synchronize();
}


extern "C"
JNIEXPORT jint JNICALL
Java_qing_albatross_demo_StalkerTest_joinTargetThread(JNIEnv *env, jclass clazz) {
  (void) env;
  (void) clazz;
  pthread_join(stalker_target_thread, NULL);
  return stalker_target_result;
}



