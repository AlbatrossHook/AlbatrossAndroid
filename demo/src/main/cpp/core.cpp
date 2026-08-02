#include <jni.h>
#include "albatross.h"
#include <pthread.h>

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