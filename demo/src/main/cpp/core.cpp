#include <jni.h>
#include "albatross.h"


extern "C" jint JNICALL JNI_OnLoad(JavaVM *vm, void *reserved) {
    return JNI_VERSION_1_6;
}


extern "C"
JNIEXPORT  JNICALL jboolean
Java_qing_albatross_demo_AlbatrossDemoMainActivity_registerAlbatrossLib(JNIEnv *env, jclass clazz,
                                                                        jclass albatross) {
    int err_code = AlbatrossAndroidInit(env, albatross);
    if (err_code)
        return JNI_FALSE;
    return JNI_TRUE;
}