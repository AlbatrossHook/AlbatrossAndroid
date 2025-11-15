#include <jni.h>
#include "albatross.h"


extern "C" jint JNICALL JNI_OnLoad(JavaVM *vm, void *reserved) {
    return JNI_VERSION_1_6;
}


extern "C"
JNIEXPORT  JNICALL jstring
Java_qing_albatross_demo_AlbatrossDemoMainActivity_registerAlbatrossLib(JNIEnv *env, jclass clazz,
                                                                        jclass albatross,int version) {
    int current_version = AlbatrossGetVersion();
    if(current_version!=version){
        return env->NewStringUTF("native库过时，请从github下载最新的so库");
    }
    int err_code = AlbatrossAndroidInit(env, albatross);
    if (err_code){
        return env->NewStringUTF("初始化失败，请拉取最新的代码");
    }
    return nullptr;
}