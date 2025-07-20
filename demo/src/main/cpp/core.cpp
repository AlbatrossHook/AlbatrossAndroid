#include <jni.h>
#include "albatross.h"


extern "C"
JNIEXPORT  JNICALL jboolean
Java_qing_albatross_demo_AlbatrossDemoMainActivity_registerAlbatrossLib(JNIEnv *env, jclass clazz,
                                                                        jclass albatross) {
    int err_code = AlbatrossAndroidInit(env, clazz);
    if (err_code)
        return JNI_FALSE;
    return JNI_TRUE;
}