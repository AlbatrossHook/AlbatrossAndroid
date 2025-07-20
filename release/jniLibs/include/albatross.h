//
// Created by QingWan on 24-5-15.
//

#ifndef ALBATROSS_HOOK_ALBATROSS_H
#define ALBATROSS_HOOK_ALBATROSS_H

#include "base_types.h"

#ifdef __cplusplus
extern "C" {
#define API extern "C"  [[maybe_unused]] __attribute__((visibility("default")))
#else
#define API __attribute__((visibility("default")))
#endif


API void *AlbDlsym(void *handle, const char *name);

API void *AlbDlopen(int pid, const char *libpath, int flags);

API void *AlbDlopenSelf(const char *libpath, int flags);

API int AlbDlclose(void *handle);

typedef struct InvocationContext InvocationContext;

typedef void (*enter_listener)(InvocationContext *invocationContext, void *data);

typedef void (*leave_listener)(InvocationContext *invocationContext, void *data);

API int AlbatrossTransactionBegin();

API int AlbatrossTransactionEnd();

API void AlbatrossTransactionForceEnd();

API int AlbatrossHookFunc(void *address, void *replace_func, void **origin_func);

API int AlbatrossUnHook(void *address);

typedef enum {
  INSTRUMENT_NOTHING = 0,
  INSTRUMENT_NO_GUARD = 0x1,
  INSTRUMENT_IGNORE_GUARD = 0x2,
} InstrumentFlags;

API long AlbatrossHookInstrument(void *address, enter_listener on_enter, leave_listener on_leave, void *user_data,
    int instrumentFlags);

API unsigned long AlbatrossGetNthArgument(InvocationContext *invocationContext, int nth);

API unsigned long AlbatrossGetResult(InvocationContext *invocationContext);

API unsigned long AlbatrossGetSP(InvocationContext *invocationContext);

API void AlbatrossSetNthArgument(InvocationContext *invocationContext, int nth, unsigned long value);

API void AlbatrossSetReturnResult(InvocationContext *invocationContext, unsigned long value);

API void AlbatrossSetUserData(InvocationContext *invocationContext, void *data);

API void *AlbatrossGetUserData(InvocationContext *invocationContext);

API void AlbatrossDestroy();

API int AlbatrossAndroidInit(void *env/*JNIEnv*/, void *Albatross/*jclass*/);


typedef enum {
  ALB_CPU_INVALID = 0,
  ALB_CPU_ARM = 1,
  ALB_CPU_ARM64 = 2,
  ALB_CPU_IA32 = 3,
  ALB_CPU_AMD64 = 4,
} AlbCpuType;

static inline AlbCpuType current_cpu_type() {
#if defined(__x86_64__)
  return ALB_CPU_AMD64;
#elif defined(__i386__)
  return ALB_CPU_IA32;
#elif defined(__aarch64__)
  return ALB_CPU_ARM64;
#elif defined(__arm__)
  return ALB_CPU_ARM;
#else
  return ALB_CPU_INVALID;
#endif
}

typedef enum AlbPageProtection {
  ALB_PAGE_NO_ACCESS = 0,
  ALB_PAGE_READ = (1 << 0),
  ALB_PAGE_WRITE = (1 << 1),
  ALB_PAGE_EXECUTE = (1 << 2),
  ALB_PAGE_SHARED = (1 << 3),
  ALB_PAGE_PRIVATE = (1 << 4),
} AlbPageProtection;
#define ALB_PAGE_RW ((AlbPageProtection) (ALB_PAGE_READ | ALB_PAGE_WRITE))
#define ALB_PAGE_RX ((AlbPageProtection) (ALB_PAGE_READ | ALB_PAGE_EXECUTE))
#define ALB_PAGE_RWX ((AlbPageProtection) (ALB_PAGE_READ | ALB_PAGE_WRITE | \
    ALB_PAGE_EXECUTE))

typedef struct WatchContext WatchContext;
typedef struct WatchHandler WatchHandler;
typedef enum AlbMemoryOperation {
  ALB_MEM_READ = 1,
  ALB_MEM_WRITE = 2,
  ALB_MEM_EXEC = 4,
} AlbMemoryOperation;

typedef struct {
  union {
    i8 v_i8;
    u8 v_u8;
    i16 v_i16;
    u16 v_u16;
    i32 v_i32;
    u32 v_u32;
#if __LP64__ || __arm__ || 1
    i64 v_i64;
    u64 v_u64;
    struct {
      u32 v1;
      u32 v2;
    } _u64;
#endif
    usize v_world;
  };
  i8 size;
} MemoryVal;

typedef void (*WatchCallback)(WatchContext *, addr_t address,
    AlbMemoryOperation operation, MemoryVal *v, void *userdata);

API WatchHandler *AlbatrossWatchMemory(void *address, usize meme_size,
    void *userdata, AlbPageProtection access_mask, WatchCallback callback);

API void AlbatrossWatchRemove(WatchHandler **context);

#ifdef __cplusplus
};
#endif
#endif //ALBATROSS_HOOK_ALBATROSS_H
