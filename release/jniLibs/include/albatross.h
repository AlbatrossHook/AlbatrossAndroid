//
// Created by QingWan on 24-5-15.
//

#ifndef ALBATROSS_HOOK_ALBATROSS_H
#define ALBATROSS_HOOK_ALBATROSS_H

#ifdef __cplusplus
extern "C" {
#endif

typedef struct InvocationContext InvocationContext;
typedef void (*enter_listener)(InvocationContext *invocationContext, void *data);
typedef void (*leave_listener)(InvocationContext *invocationContext, void *data);
int AlbatrossTransactionBegin();
int AlbatrossTransactionEnd();
int AlbatrossGetVersion();
int AlbatrossHookFunc(void *address, void *replace_func, void **origin_func);
int AlbatrossUnHook(void *address);
typedef enum {
    INSTRUMENT_NOTHING = 0,
    INSTRUMENT_NO_GUARD = 0x1,
    INSTRUMENT_IGNORE_GUARD = 0x2,
} InstrumentFlags;

long AlbatrossHookInstrument(void *address, enter_listener on_enter, leave_listener on_leave,
                             void *user_data, int instrumentFlags);
unsigned long AlbatrossGetNthArgument(InvocationContext *invocationContext, int nth);
unsigned long AlbatrossGetResult(InvocationContext *invocationContext);
unsigned long AlbatrossGetSP(InvocationContext *invocationContext);

void
AlbatrossSetNthArgument(InvocationContext *invocationContext, int nth, unsigned long value);
void AlbatrossSetReturnResult(InvocationContext *invocationContext, unsigned long value);
void AlbatrossSetUserData(InvocationContext *invocationContext, void *data);
void *AlbatrossGetUserData(InvocationContext *invocationContext);
int AlbatrossAndroidInit(void *env/*JNIEnv*/, void *Albatross/*jclass*/);


#ifdef __cplusplus
};
#endif
#endif //ALBATROSS_HOOK_ALBATROSS_H
