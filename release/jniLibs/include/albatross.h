//
// Created by QingWan on 24-5-15.
//

#ifndef ALBATROSS_HOOK_ALBATROSS_H
#define ALBATROSS_HOOK_ALBATROSS_H

//#include "base_types.h"

#ifdef __cplusplus
extern "C" {
#define API extern "C"  [[maybe_unused]] __attribute__((visibility("default")))
#else
#define API __attribute__((visibility("default")))
#endif
#define ALBATROSS_VERSION 7

API int AlbatrossGetVersion();

API void *AlbDlsym(void *handle, const char *name);

API void *AlbDlopen(int pid, const char *libpath, int flags);

API void *AlbDlopenSelf(const char *libpath, int flags);

/* 模块基址(vaddr 0 的运行时地址), 配合符号地址可换算偏移: offset = symbol - base。
 * handle 来自 AlbDlopen/AlbDlopenSelf。 */
API void *AlbGetModuleBase(void *handle);

/*
 * 注册"期望基址": 同一文件被加载多份时(典型是 libart —— 我们 dlopen 一份、
 * ART 初始化时在 classloader namespace 又加载一份), AlbDlopen/AlbDlsym 按名字
 * 扫 maps 只能挑到其中一份, 而符号地址与静态数据是每份独立的, 挑错会得到
 * "JNI_GetCreatedJavaVMs 返回 0 个 VM"这类难查的失败。
 * 用 dladdr(正在使用的函数地址) 取出自己那份的基址(dli_fbase)注册后, 同名其它
 * 实例一律跳过; 传 NULL 取消注册。
 */
API void AlbSetPreferredModuleBase(const char *libpath, void *base);

API int AlbDlclose(void *handle);


/* 回调第 4 参是"该次映射的文件偏移"(多数平台为 0), 不是模块大小——
 * 要模块区间请自解析 /proc/pid/maps。坑见
 * doc/pitfalls/pitfall-enumerate-modules-param-20261005.md */
typedef int(*search_func)(void *data, const char *name, void *addr, unsigned long file_offset);

API void *AlbIterSymbol(void *handle, search_func func, void *data);


API void AlbEnumerateModules(int pid, search_func callback, void *data);

typedef struct InvocationContext InvocationContext;

typedef void (*enter_listener)(InvocationContext *invocationContext, void *data);

typedef void (*leave_listener)(InvocationContext *invocationContext, void *data);

typedef void (*DetachCallback)(void *func, void *user_data);

API int AlbatrossTransactionBegin();

API int AlbatrossTransactionEnd();

API void AlbatrossTransactionForceEnd();

/*
 * W^X 模式开关（默认关闭）：给"禁 RWX 的 seccomp 目标"用 —— minijail 白名单
 * 可以只放行 RW 与 RX、拒绝 W|X 组合的 mprotect/mmap（如 qcrilNrd 的
 * /vendor/etc/seccomp_policy/qcrilnr@2.0.policy），此时把代码页翻 RWX 会被
 * 策略 SIGSYS 直接杀掉进程（libminijail log_sigsys_handler）。打开后：
 *  - 目标入口只写单字(4B)补丁，经免翻转后端（/proc/self/mem 的 pwrite，
 *    内核 FOLL_FORCE）写回，全程不发 mprotect；
 *  - 跳板批次只复用未提交(RW)页，不复用已提交(RX)批次；
 *  - memfd 批次自动退回匿名批次（memfd 的 RX 视图写不进，且策略通常没有
 *    memfd_create）。
 * 该模式的开关必须显式声明：不要用"试一次 mprotect(RWX)"探测能力
 * （minijail 的动作是 SIGSYS，一探即死）。返回 0 成功。
 * 机制与实测见 doc/framework_feedback/req-wxorx-seccomp-no-rwx-20261009.md
 */
API int AlbatrossSetWxorxMode(int enable);

API int AlbatrossHookFunc(void *address, void *replace_func, void **origin_func);

API void *AlbatrossGetBackup(void *address);

API int AlbatrossUnHook(void *address);

typedef enum {
    INSTRUMENT_NOTHING = 0,
    INSTRUMENT_NO_GUARD = 0x1,
    INSTRUMENT_IGNORE_GUARD = 0x2,
} InstrumentFlags;

/* 目标函数是否走"间接返回"约定（arm64 = 入口处先读 x8，sret 缓冲指针；HIDL 的
 * Return<void>、>16 字节的结构体返回都是这一形态）。返回 1/0，-1 = 判不了（本架构
 * 不做判定，或地址读不出可重定位的入口）。没装过钩也能查：只读分析，不写任何内存。
 *
 * 用途：替换式（AlbatrossHookFunc）装这种目标时，replacement **必须**按同样的返回
 * 约定声明（arm64 上声明成 >16 字节的结构体/非平凡类型，让编译器把调用方的 sret 缓冲
 * 原样转给 backup 调用）；声明成 void/标量会让原函数经 backup 拿到垃圾 x8，目标在写
 * 自己的返回值时崩。框架在装替换式钩子时会对这种目标告警一次；想先查再决定就用这个。
 * 纯观测请用 AlbatrossHookInstrument（派发层对寄存器是 ABI 透明的）。
 * 坑见 doc/pitfalls/pitfall-scratch-reg-clobbers-sret-20261010.md */
API int AlbatrossTargetUsesIndirectReturn(void *address);

/* 返回值 = listener id(>= 1) 或负错误码；**id 从 1 起编号，0 不是合法 id**。
 * 用 "id > 0" 判装钩成功、把该 id 交给 AlbatrossUnHookInstrument 卸载；id 为负 = 装钩失败。
 *
 * **不需要 leave 就把 on_leave 传 NULL**（只观测 enter 时这是零代价路径：不注册 leave
 * 监听器 ⇒ 派发层不种"改写入参返回地址"的陷阱）。**别传"空实现"充数**——空实现同样会计入
 * on_leave_listener_num、照样把陷阱装上。
 *
 * **on_leave 只对"函数入口站点"生效**：leave 派发靠改写进入目标时保存的返回地址实现，
 * 挂钩点落在函数中段（典型的"符号基址 + 偏移"观测点）时，被钩代码在到达挂钩点之前就
 * 把真返回地址存进了自己的帧，返回不走被改写的那份 ⇒ leave 永远到不了（且该条目在
 * 修复前的版本上只增不减、会与入口站点的 leave 错账崩）。arm64 装钩期会按站点形态判定
 * （前一条指令 / 站点首指令，见 src/interceptor/site_shape.h）并把这类站点自动降级为
 * enter-only：**条目不泄漏、on_leave 不触发**（装钩时 AlbLog 告警一次）。
 * 中段点观测请只注册 on_enter（on_leave 传 NULL）。机制与实测见
 * doc/pitfalls/pitfall-instrument-midfunc-leave-20261014.md */
API long AlbatrossHookInstrument(void *address, enter_listener on_enter, leave_listener on_leave,
                                 DetachCallback on_detach, void *user_data, int instrumentFlags);

/* listenerId >= 1 = 只卸该监听器；< 0 = 卸该地址上的全部监听器。 */
API int AlbatrossUnHookInstrument(void *address, long listenerId);

/*
 * 库加载通知(由 dlopen 触发)。触发器:
 *  - Android: bionic linker 的 do_dlopen(dlopen/android_dlopen_ext/__loader_dlopen
 *    都汇入该函数);
 *  - Linux(glibc): libc 的 dlopen/dlmopen(glibc >= 2.34 起 dl* 全部并入 libc)。
 * 通知在真实 dlopen 成功返回之后、dlopen 返回其调用方之前, 由调用线程同步分发;
 * 此时加载器锁已释放, 回调里可以安全地做 ELF 解析、安装 hook 等操作。
 * 无法安装触发器的环境(如无 libc/linker 的进程)注册返回负值。
 */
typedef int (*library_load_listener)(const char *lib_name, void *handle, int flags,
                                     void *user_data);

/* lib_filter 非空时只回调请求名包含该子串的库加载; 返回监听 id(>= 0)或负错误码。
 * 回调返回非 0 继续监听, 返回 0 自动注销(回调自身的 user_data 由调用方清理)。 */
API long AlbatrossRegisterLibraryCallback(const char *lib_filter,
                                          library_load_listener listener, void *user_data);

/* 注销监听。返回后不会再分发新事件, 但可能有一次已进入分发的回调正在执行,
 * 因此不要在注销后立即释放 user_data(可在回调返回后再释放)。 */
API int AlbatrossUnregisterLibraryCallback(long id);

/*
 * 延迟 hook: 目标库尚未加载时先登记, 库被 dlopen 加载后自动解析符号并安装,
 * 安装完成早于 dlopen 返回其调用方; 注册时库已加载则在注册调用内同步安装。
 * lib_name 对进程 maps 路径做子串匹配(如 "libfoo.so")。
 *
 * 完成回调 state 取值 ALB_DELAYED_APPLIED / ALB_DELAYED_FAILED, 在触发线程上
 * 同步执行(注册时已安装则在注册调用内执行); 成功时 symbol_address 与 backup
 * 有效(替换型 backup 为原函数跳板, 插桩型 backup 恒为 0), 失败时均为 0。
 * 回调里可以调用框架 API(含取消本条目), 但不要再 dlopen。
 * user_data 同时作为插桩型 hook 的 instrument data(enter/leave 监听器的第二个参数)。
 * 每个延迟 hook 只生效一次: 目标被 dlclose 后重新加载不会自动重挂。
 */
enum {
    ALB_DELAYED_PENDING = 0,
    ALB_DELAYED_APPLIED = 1,
    /* 库已加载但符号未找到/不可 hook, 或 hook 安装失败 */
    ALB_DELAYED_FAILED = 2,
};

enum {
    ALB_DELAYED_ERR_ARG = -1,
    /* 目标库未加载且当前环境无法安装加载触发器 */
    ALB_DELAYED_ERR_TRIGGER = -2,
    ALB_DELAYED_ERR_MEMORY = -3,
};

typedef void (*delayed_hook_callback)(long id, int state, void *symbol_address,
                                      void *backup, void *user_data);

API long AlbatrossHookFuncDelayed(const char *lib_name, const char *symbol_name,
                                  void *replace_func, delayed_hook_callback on_result, void *user_data);

/* 语义同 AlbatrossHookInstrument（含 on_leave 的 NULL 约定与"只对函数入口站点生效"，
 * 见上面的长注释）：不需要 leave 就传 NULL，别传空实现。 */
API long AlbatrossHookInstrumentDelayed(const char *lib_name, const char *symbol_name,
                                        enter_listener on_enter, leave_listener on_leave, DetachCallback on_detach,
                                        delayed_hook_callback on_result, void *user_data, int instrumentFlags);

/*
 * 按偏移登记延迟 hook: 目标符号拿不到(ID 里只看到地址/未导出符号)时用。
 * offset 为 ELF 虚拟地址(IDA/Ghidra/readelf 里看到的 vaddr), 实际挂钩地址 =
 * 模块基址 + offset; 模块基址与 AlbEnumerateModules 回调给出的地址一致。
 * 库已加载时可先 AlbDlsym 拿到符号地址再减模块基址得到 offset。
 * 其余语义(安装时机/状态/取消/回调)与按符号名的版本完全一致。
 */
API long AlbatrossHookFuncDelayedOffset(const char *lib_name, unsigned long offset,
                                        void *replace_func, delayed_hook_callback on_result, void *user_data);

/* 按偏移观测（多为函数中段）：on_enter 必需，on_leave **几乎总是应该传 NULL**
 * （中段站点上留不到 leave，注册它只会让派发层白装一个到不了的陷阱）。 */
API long AlbatrossHookInstrumentDelayedOffset(const char *lib_name, unsigned long offset,
                                              enter_listener on_enter, leave_listener on_leave, DetachCallback on_detach,
                                              delayed_hook_callback on_result, void *user_data, int instrumentFlags);

/* 状态查询; 未知 id 返回 ALB_DELAYED_ERR_ARG, 未解析地址/备份返回 0 */
API int AlbatrossGetDelayedHookState(long id);

API void *AlbatrossGetDelayedHookAddress(long id);

API void *AlbatrossGetDelayedHookBackup(long id);

/* 取消: 等待中直接移除; 已安装则先卸载再移除。0 成功。 */
API int AlbatrossCancelDelayedHook(long id);

/*
 * ---- PLT hook（GOT/导入表 hook, 移植 bytehook 的 manual 模式）----
 *
 * 与 AlbatrossHookFunc（改目标函数入口的代码页）不同, PLT hook 改写的是
 * **caller 模块自己的 GOT 槽**: 只影响该 so 经导入表发出的调用, 不动被调方的
 * 代码, 天然不受 W^X/禁 RWX 的 seccomp 策略限制。适合"分析某个 so 的行为":
 * 钩它调用的 libc/libfoo 函数、观测/改写参数与返回值、按 so 精确限定范围。
 * 机制、与 hookFunc 的分工与限制见 doc/architecture/plt-hook.md。
 *
 * 语义(对齐 bytehook):
 *  - caller_path_name: 目标 caller 模块。不含 '/' 时按 basename 后缀匹配
 *    (如 "libfoo.so"), 含 '/' 时要求全等; callee_path_name 同规则、可传 NULL
 *    (非空时只钩"槽位确实解析到该模块"的导入, 处理同名多实现/namespace)。
 *  - AlbPltHookSingle 绑定第一个匹配的已加载模块; 未加载则挂起等待, 库被
 *    dlopen 后自动安装(早于 dlopen 返回其调用方)。Partial/All 作用于当前全部
 *    匹配模块, 并同样覆盖之后加载的模块。
 *  - 同一 (模块, 符号) 同时只允许一个 PLT 钩: 后到的任务对该模块报
 *    ALB_PLT_ERR_DUP。"模块没导入该符号"对 Partial/All 静默跳过, 对 Single 报
 *    ALB_PLT_ERR_NO_SYMBOL。
 *  - hooked 回调对**每个生效的 caller 模块**各触发(两阶段, 见下)。
 *  - unhook 把 GOT 写回原值; 恢复前校验模块仍在原 path+基址、槽值仍是本任务装
 *    的代理(库已卸载/被外力改过就跳过, 不误写)。
 *  - 主可执行体、匿名映射、框架自身库不会被钩(同 bytehook)。主可执行体的识别不
 *    只看 dlpi_name: bionic 上它不是空串(glibc 是), 另按 auxv 的 AT_PHDR 比对
 *    phdr 地址。
 *
 * 两阶段回调(原函数地址的交付时序, 重要):
 *  1. status == ALB_PLT_STATUS_ORIG_ADDR: 发生在**写 GOT 之前**, prev_func =
 *     该模块槽位原本的目标(原函数)。写完后代理随时可能被执行, 所以**必须在这次
 *     回调里立刻把 prev_func 存好**(通常存进全局变量), 代理里才能调原函数。
 *  2. status == ALB_PLT_OK 或 ALB_PLT_ERR_*: 该模块的最终结果, prev_func 仍是
 *     原函数。写失败时只有第一阶段回调有效(此时 GOT 未被改写)。
 *
 * 回调在框架锁内执行: 可以再调 AlbPlt* API(同线程可重入), 但不要跨线程等待
 * 另一个持锁路径; 回调里调原函数是安全的(不取框架锁)。
 *
 * 支持的槽位: .rel.plt(JUMP_SLOT) 与 .rel.dyn(GLOB_DAT/ABS, 含 APS2 压缩格式;
 * 即 .data.rel.ro 里的函数指针这类"数据里的导入地址"也可被钩)。glibc 懒绑定
 * 未解析的槽位也能钩: orig 用符号解析出的真身(Android/bionic 恒 eager)。
 *
 * **延迟语义(与 AlbatrossHookFuncDelayed 同一模式)**: 三种入口都是"目标库未加载
 * 就先登记, 加载后自动安装"——caller 还没加载时任务挂起(不报错、不回调; 只有装不上
 * 加载通知的静态环境才报 ALB_PLT_ERR_TRIGGER), 该 so 被 dlopen 时在 **dlopen 返回
 * 其调用方之前**完成安装, 因此它的第一次调用就已被接管; 库被 dlclose 后重新加载也会
 * 重新安装(比 AlbatrossHookFuncDelayed 的"只生效一次"更强); callee_path_name 指定的
 * 库晚于 caller 加载时同样会补解析。**不需要单独的 delayed API**。
 *
 * 与其它 hook 层的关系: 与 AlbatrossHookFunc / AlbatrossHookInstrument(入口补丁)
 * **完全独立、可叠加**——同一符号两层同时钩住时 PLT 层只影响该 so 的导入调用、入口层
 * 影响全进程, 互不干扰; 与 Java 方法 hook 无交集(Java 方法调用与 Java→native 调用都
 * 不经 PLT)。不支持"按偏移"登记: PLT 槽本身就按导入符号名定位(名字一定在 .dynsym 里)。
 *
 * 返回值: AlbPltHook* 返回 stub(失败为 NULL: 参数非法/内存不足), 具体到模块的
 * 结果经 hooked 回调给出; AlbPltUnhook 0 = 成功。
 */
typedef void *AlbPltStub;

/* hooked 回调(两阶段语义见上) */
typedef void (*alb_plt_hooked_callback)(AlbPltStub stub, int status_code,
                                        const char *caller_path_name, const char *sym_name, void *new_func, void *prev_func,
                                        void *arg);

/* Partial 的 caller 过滤: 返回非 0 = 对该 caller 模块装钩 */
typedef int (*alb_plt_caller_filter)(const char *caller_path_name, void *arg);

enum {
    ALB_PLT_OK = 0,
    /* 回调第一阶段状态(非错误): prev_func = 该模块槽位原目标, 必须在回调里存好 */
    ALB_PLT_STATUS_ORIG_ADDR = 1,
    ALB_PLT_ERR_ARG = -1,
    ALB_PLT_ERR_MEMORY = -2,
    /* 模块没导入该符号(或 ELF 解析失败/懒绑定槽位解析不出真身) */
    ALB_PLT_ERR_NO_SYMBOL = -3,
    /* 指定了 callee_path_name, 但槽位解析目标不是它(同名多实现/namespace) */
    ALB_PLT_ERR_CALLEE = -4,
    /* 该 (模块, 符号) 已被另一个 PLT 钩占用 */
    ALB_PLT_ERR_DUP = -5,
    /* mprotect 翻写权限失败 */
    ALB_PLT_ERR_PROT = -6,
    /* 写入/写后处理失败 */
    ALB_PLT_ERR_WRITE = -7,
    /* 装不上 dlopen 加载通知(无 linker 的静态进程): 新加载 so 不会自动补钩 */
    ALB_PLT_ERR_TRIGGER = -8,
    /* 模块在忽略名单(匿名映射/自身库/用户 AlbPltAddIgnore) */
    ALB_PLT_ERR_IGNORED = -9,
};

API AlbPltStub AlbPltHookSingle(const char *caller_path_name, const char *callee_path_name,
                                const char *sym_name, void *new_func, alb_plt_hooked_callback hooked, void *hooked_arg);

API AlbPltStub AlbPltHookAll(const char *callee_path_name, const char *sym_name, void *new_func,
                             alb_plt_hooked_callback hooked, void *hooked_arg);

API AlbPltStub AlbPltHookPartial(alb_plt_caller_filter caller_filter, void *caller_filter_arg,
                                 const char *callee_path_name, const char *sym_name, void *new_func,
                                 alb_plt_hooked_callback hooked, void *hooked_arg);

/* 卸载并恢复 GOT。返回 0 成功; ALB_PLT_ERR_ARG = stub 非法(含重复卸载)。 */
API int AlbPltUnhook(AlbPltStub stub);

/* 追加 caller 忽略名单(匹配规则同 caller_path_name); 只影响之后的应用。0 成功。 */
API int AlbPltAddIgnore(const char *caller_path_name);

API unsigned long AlbatrossGetNthArgument(InvocationContext *invocationContext, int nth);

API unsigned long AlbatrossGetRegValue(InvocationContext *invocationContext, int reg);

API unsigned long AlbatrossGetNamedRegValue(InvocationContext *invocationContext, const char *regName);

API unsigned long AlbatrossGetResult(InvocationContext *invocationContext);

API unsigned long AlbatrossGetSP(InvocationContext *invocationContext);

API void AlbatrossSetNthArgument(InvocationContext *invocationContext, int nth, unsigned long value);

API void AlbatrossSetRegValue(InvocationContext *invocationContext, int nth, unsigned long value);

API void AlbatrossSetNamedRegValue(InvocationContext *invocationContext, const char *regName, unsigned long value);

API void AlbatrossSetReturnResult(InvocationContext *invocationContext, unsigned long long value);

API void AlbatrossSetUserData(InvocationContext *invocationContext, void *data);

API void *AlbatrossGetUserData(InvocationContext *invocationContext);

API void *AlbatrossGetFunc(InvocationContext *invocationContext);

API void AlbatrossDestroy();


API int AlbatrossAndroidInit(void *env/*JNIEnv*/, void *Albatross/*jclass*/);

API int AlbatrossOceanInit(void *env/*JNIEnv*/, void *ocean_tracer/*jclass*/);

API  int android_init_native(void *env, void *art_handler, int method_count);

API int
android_load_dex(const char *dex_path, const char *lib_path, const char *register_class, const char *class_name,
                 int flags, const char *loaderSymbolName, const char *parma1, int param2);

/* android_load_jvm 的 flags */
enum {
    /* 只解释执行（-Xint）：禁用 JIT/AOT，行为确定，也是 art_runner 的历史默认 */
    JVM_FLAG_INTERPRETER_ONLY = 1,
    /* -verbose:jni -verbose:gc */
    JVM_FLAG_VERBOSE = 2,
    /* -Xcheck:jni（很慢，仅排查用） */
    JVM_FLAG_CHECK_JNI = 4,
    /* -Xdebug -XjdwpProvider:internal，JDWP 端口固定 12346（server=y, suspend=n） */
    JVM_FLAG_DEBUG = 8,
    /* 创建后立即 FindClass("qing/albatross/core/Albatross") 并注册 Java 侧 native
     * (等价 art_runner 里的 AlbatrossAndroidInit 调用)；classpath 里没有该类的
     * 宿主不要带这个 flag（返回 JVM_AGENT_NOT_FOUND）。 */
    JVM_FLAG_INIT_AGENT = 16,
};

/*
 * 在没有 Java VM 的进程里创建/加载 JVM（仅 Android，且宿主必须是动态链接的
 * 构建；静态构建返回 JVM_NOT_SUPPORT）。
 *
 * 语义与 art_runner 的流程一致：
 *   dlopen(libart) + dlopen(libandroid_runtime) → JNI_CreateJavaVM →
 *   registerFrameworkNatives → (可选) AlbatrossAndroidInit →
 *   android_init_native(nullptr, art_handler, 1)
 * 成功后 Thread::GetVM()/get_current_jniEnv() 可用，android_load_dex /
 * android_load_plugin 等 Java 能力随之可用；进程里已有 VM 时直接返回
 * JVM_ALREADY_LOADED（幂等）。
 *
 *   libart_path   : 空/NULL 用设备默认 "libart.so"
 *   classpath     : JVM classpath（':' 分隔，可以是 dex/apk/jar），空则不带
 *   library_path  : -Djava.library.path（System.loadLibrary 的查找目录），空则不带
 *   flags         : JVM_FLAG_*
 * 返回值见 src/injector/injector.h 的 JvmLoadResult。
 *
 * 实现内部依赖 libart 从主可执行文件引用的 sigchain 符号，本库已导出
 * （src/android/sigchain.cpp）；前提是该库对 libart 可见（测试程序里是主可执行
 * 文件的依赖链，注入场景是 dlopen 的 caller group）。见
 * doc/jvm_in_native_process.md 与 doc/pitfalls/pitfall-libart-sigchain-20261003.md。
 */
API int android_load_jvm(const char *libart_path, const char *classpath, const char *library_path, int flags);

API void *get_current_jniEnv();


typedef enum {
    ALB_CPU_INVALID = 0,
    ALB_CPU_ARM = 1,
    ALB_CPU_ARM64 = 2,
    ALB_CPU_IA32 = 3,
    ALB_CPU_AMD64 = 4,
} AlbCpuType;

enum {
    ARG_INT = 'I',
    ARG_BOOL = 'Z',
    ARG_STR = 's',
    ARG_BYTE = 'B',
    ARG_LONG = 'J',
    ARG_VOID = 'V',
    ARG_JSON = 'j',
    ARG_SHORT = 'S',
    ARG_FLOAT = 'F',
    ARG_DOUBLE = 'D',
    ARG_CHAR = 'C',
    ARG_BYTES = 'b',
    ARG_PTR = 'p',
};
typedef char ArgType;


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
        signed char v_i8;
        unsigned char v_u8;
        short v_i16;
        unsigned short v_u16;
        int v_i32;
        unsigned int v_u32;
#if __LP64__ || __arm__ || 1
        long long v_i64;
        unsigned long long v_u64;
        struct {
            unsigned int v1;
            unsigned int v2;
        } _u64;
#endif
        unsigned long v_world;
    };
    signed char size;
} MemoryVal;

typedef void (*WatchCallback)(WatchContext *, unsigned long address,
                              AlbMemoryOperation operation, MemoryVal *v, void *userdata);

/* 监视 [address, address+size) 的读/写。传入带 TBI 标签的指针（bionic malloc
 * 在 arm64 上的默认形态）是受支持的（需 rwbp ≥0.4.27；旧模块见 AlbRwbpWatchType
 * 段的标签说明）：命中上报的地址统一是剥标签形态（与解释器后端一致）。 */
API WatchHandler *AlbatrossWatchMemory(void *address, unsigned long meme_size,
                                       void *userdata, AlbPageProtection access_mask, WatchCallback callback);

API void AlbatrossWatchRemove(WatchHandler **context);

/* ---- rwbp 内核读写断点（in-process 优先）----
 * 在**当前进程**里直接设点：一次 prctl 即可生效，不需要 RPC 往返，也不
 * 需要 root（目标与调用者同 uid/euid 即可）；跨进程/跨 uid 需要 root。
 * 内核侧实现见 kpms/rwbp。本族返回值约定：除 AlbRwbpWatchSupported()
 * （1/0）外，成功 >=0、失败 <0 且值即 -errno（裸 syscall 不设置 errno
 * 变量，勿写 "-errno" 转换——坑见 doc/pitfalls/pitfall-inline-syscall-errno-20261005.md）；
 * 模块未加载时 Supported()=0、其余接口返回负值（内核按未知 prctl 选项拒绝）。
 *
 * 读/写判据、调用点寄存器与事件序列都由内核侧给出，调用方不做指令模拟；
 * 限制见 kpms/rwbp/poc/README.md：普通断点在同页出现非命中访问后会把整页
 * 放开 5ms，窗口内的命中会静默丢失，热页上必须按尽力而为对待——**模块
 * ≥0.4.28 起这个盲窗有台账**（`AlbRwbpWatchStatusV2` + `AlbRwbpWatchStatsV2` 直读 win_opens/
 * win_open_now/win_ns/uaccess_opens + filtered_*）；≤0.4.27 无计数，只能用
 * 下面的盲区探针采样推断。
 *
 * 监视目标必须选**安静的数据页**：
 * - **arm 之前页必须已在页表里**：模块的 ADD 立刻保护该页，`mmap` 后没碰过的页
 *   （PTE 不存在）会返回 **-EFAULT(-14)**——目标自己先写一次该页，或让 ADD 前
 *   有一次真实访问；坑见 doc/pitfalls/pitfall-rwbp-watch-page-must-be-present-20261014.md；
 * - 不要指向代码页或与代码同页的缓冲（tcc cmodule 的 static 缓冲与代码同页）：
 *   保护代码页后取指即陷阱，目标线程会陷入内核态空转（2026-10-05 两台真机实测）；
 * - 启用了堆标签（TBI/scudo）的 app（Android 12+ 常见）上监视活动堆块后，
 *   free() 可能因指针标签在内核单步路径被清洗而 abort（"Pointer tag was
 *   truncated"）——这类目标改用 AlbatrossWatchMemory（页保护）或快照比对。
 * - **TBI 标签地址**：模块 ≥0.4.27 在用户地址边界做 untag 归一（与内核
 *   `untagged_addr()` 一致），带标签/剥标签的 addr 都能用，命中上报也是剥标签形态；
 *   模块 ≤0.4.26 的命中判据按原始 far/页**整值比较、不剥标签**，而目标代码用带标签
 *   堆指针访存时，规则（通常由框架按剥标签形态 arm）就会判不中 → `!matched_page`
 *   交回内核而页面 PTE_USER 已清 → 访存指令原地重试（**故障风暴**：线程 R 态自旋、
 *   SIGKILL 投不进、可拖重启设备），或静默丢事件（written=0）。旧模块下监视带标签
 *   堆指针务必先升级模块；坑见 doc/pitfalls/pitfall-rwbp-tbi-tagged-address-20261009.md。
 *
 * 事件通道是模块级单例环形缓冲（同时只能有一个 enable 者），由 enable 的
 * 一方独占读取；in-process 用法即"自己 enable、自己 drain"。
 */
typedef enum AlbRwbpWatchType {
    ALB_RWBP_READ = 1,
    ALB_RWBP_WRITE = 2,
    ALB_RWBP_RW = 3,
} AlbRwbpWatchType;

typedef struct AlbRwbpWatchEvent {
    unsigned long long seq;  /* 模块内单调递增，缺口即丢弃 */
    unsigned long far;       /* 触发访问的地址 */
    unsigned long pc;        /* 触发指令 */
    unsigned long lr;        /* 调用点（x30） */
    unsigned long sp;
    unsigned long regs[31];  /* x0..x30，可反解访存宽度与写入值；v2 会话
                            * （EnableV2）另有 data[] 数据字节可直接取值 */
    unsigned long rule_addr;
    unsigned int kind;       /* 0=命中 1=PC bypass 2=同页守卫 3=PAGE_OPEN(v2) */
    unsigned int is_write;
    unsigned int esr;
    unsigned int rule_len;
    int tid;
    int tgid;
} AlbRwbpWatchEvent;

/* 模块是否在位（1/0） */
API int AlbRwbpWatchSupported(void);

/* tid=0 表示当前线程所在进程；addr/size 为要监视的字节范围，type 见上 */
API int AlbRwbpWatchAdd(int tid, void *address, unsigned long size, int type);
API int AlbRwbpWatchDel(int tid, void *address);

/* 打开本进程（tgid）的事件通道；内部先停掉旧通道（含 Guard 采样）再使能，
 * 重复调用=重建环。ring_pages 为环大小（4KiB 页数），0 用模块默认（64 页；
 * 模块 ≤0.4.27 容量 704 条，≥0.4.28 按 424B 记录分配、容量 576 条，v1/v2
 * 会话同此——以返回值为准）。
 * 返回：**>=0 = 环可容纳的记录数（成功不是返回 0！）**；<0 = -errno。
 * 成功判据必须写 `ret >= 0`——写成 `ret == 0` 会把成功误判成失败
 * （2026-10-05 评估轮即因此把工作正常的模块判成"不可用"）。
 * Status 的返回值 >=0 = 当前可读条数（pending）；Drain/Stop 同样遵循上面的负值=-errno 约定。 */
API int AlbRwbpWatchEnable(unsigned int ring_pages);
/* 返回取到的事件数（<0 为 -errno）；max_events 为 events 数组容量 */
API int AlbRwbpWatchDrain(AlbRwbpWatchEvent *events, int max_events);
API int AlbRwbpWatchStatus(unsigned long *written, unsigned long *dropped, int *pid_filter);
API int AlbRwbpWatchStop(void);

/* ---- rwbp v2 事件通道（模块 ≥0.4.28；逐会话 opt-in）----
 * 用 AlbRwbpWatchEnableV2 打开的会话里，事件在 v1 字段之后追加：
 *   - time_mono_ns / time_real_ns：fault 时刻时间戳；模块缺 ktime 符号时恒 0，
 *     可用性看 AlbRwbpWatchStatusV2 填出的 ts_src 位掩码；
 *   - data[]/data_len/data_flags：数据字节快照。**读**事件=fault 时刻该地址处
 *     仍是访问前的内存；**写**事件=单步执行后的落地字节（data_flags 带
 *     ALB_RWBP_DATA_AFTER），stp/SIMD 等无法从寄存器重建的写也能直接拿到。
 *     注意 data_len 是"从 far 起页内连续可读的字节数"（上限 32），**不是访问
 *     宽度**——宽度仍要从指令/寄存器解；
 *   - kind 可能取 3=PAGE_OPEN：该页被放开的盲窗记录（带时间戳；EL1 uaccess
 *     放开的那条不带寄存器）。**它不是对监视地址的访问**，且 far 可能落在
 *     监视范围里（例如 write-only 监视下读该页任意字节都会记一条、far=被读
 *     地址）——消费者必须按 kind 过滤，只按地址范围过滤会冒出假命中；
 *     坑见 doc/pitfalls/pitfall-rwbp-v2-page-open-records-20261014.md。
 * 降级语义（模块 ≤0.4.27）：
 *   - EnableV2 请求了 v2 专属选项（flags / pc 过滤）→ -ENOTSUP；不带这些
 *     选项时自动退化为 v1 会话；
 *   - DrainV2 照常可用但 v2 尾全 0；StatusV2 的 v2 字段全 0（ts_src=0、
 *     rec_size=0）——据此判断"本次会话没有 v2 数据"。
 *   - **记录尺寸不符**（模块侧 rec_size != 本地 sizeof(struct rwbp_trace_rec)，
 *     未来 ABI 漂移）：drain 自动退回 v1 前缀语义（v2 尾巴全 0，与旧模块同）；
 *     StatusV2 仍如实报 rec_size/abi_version，据此能看出为什么没有尾巴。
 * 盲窗台账：只要模块 ≥0.4.28，StatusV2 的 win_opens/win_open_now/win_ns/
 * uaccess_opens 与 filtered_pid/filtered_pc 就有计数（v1 会话也有，计数与
 * v1/v2 无关）；≤0.4.27 全 0，只能靠上面的探针。注意这几个是**模块全局**计数：
 * 数的是"会话期间模块里任何一条规则的页"（别的 rwbp 使用者、框架自己的
 * modify 规则页都算），不区分 mm——要判"**你监视的那页**有没有被放开过"，
 * 用 v2 会话里的 kind=3 PAGE_OPEN 记录（in-process 的 pid_filter=tgid，天然
 * 只剩本进程的）。 */
typedef struct AlbRwbpWatchEventV2 {
    AlbRwbpWatchEvent v1;            /* v1 前缀（冻结布局，可直接 memcpy 出来） */
    unsigned long long time_mono_ns; /* CLOCK_MONOTONIC；ts_src 位 0 有效 */
    unsigned long long time_real_ns; /* CLOCK_REALTIME；ts_src 位 1 有效 */
    unsigned int data_len;           /* data[] 有效字节数；0=未捕获 */
    unsigned int data_flags;         /* ALB_RWBP_DATA_AFTER：写事件为单步后字节 */
    unsigned char data[32];          /* 自 far 起、页内裁剪，上限 32 字节 */
} AlbRwbpWatchEventV2;

#define ALB_RWBP_DATA_MAX   32u
#define ALB_RWBP_DATA_AFTER 1u  /* data[] 为指令执行后的内存 */
#define ALB_RWBP_FLAG_NO_DATA 1u /* 跳过每记录数据快照（省一次页表遍历） */
#define ALB_RWBP_TS_MONO    1u  /* time_mono_ns 有效 */
#define ALB_RWBP_TS_REAL    2u  /* time_real_ns 有效 */

typedef struct AlbRwbpWatchEnableOptions {
    unsigned int ring_pages; /* 0 用模块默认；含义同 AlbRwbpWatchEnable */
    unsigned int flags;      /* ALB_RWBP_FLAG_*（仅 v2 会话） */
    unsigned long pc_start;  /* PC 包含过滤 [start,end)；0/0=关闭（仅 v2） */
    unsigned long pc_end;
} AlbRwbpWatchEnableOptions;

typedef struct AlbRwbpWatchStatsV2 {
    unsigned long written;   /* 模块环：已记录条数 */
    unsigned long dropped;   /* 模块环：被覆盖丢弃（可见丢失） */
    unsigned long capacity;  /* 环容量（条） */
    unsigned long pending;   /* 当前可读条数 */
    int pid_filter;
    /* ---- v2（模块 ≤0.4.27 时全 0；台账字段 ≥0.4.28 恒有效） ---- */
    unsigned long rec_size;      /* 模块侧记录大小；与本地 sizeof 比对查 ABI 漂移 */
    unsigned long abi_version;   /* 模块 RWBP_VERSION_CODE */
    unsigned long ts_src;        /* ALB_RWBP_TS_* 位掩码；0=时间不可用 */
    unsigned long filtered_pid;  /* 被 pid_filter 拒掉的记录数 */
    unsigned long filtered_pc;   /* 被 PC 包含过滤拒掉的记录数 */
    /* 下面四行都是**模块全局**计数（会话期间模块里任何规则的页，不区分 mm）——
     * 只作信息透出；"你的页是否被放开"看 kind=3 PAGE_OPEN 记录。 */
    unsigned long win_opens;     /* 盲窗（grace 窗口）打开次数，按页计 */
    unsigned long win_open_now;  /* 当前仍放开的页数 */
    unsigned long long win_ns;   /* 已关闭盲窗的累计开放时长 */
    unsigned long uaccess_opens; /* EL1 uaccess 导致的放开（不会自动回保护） */
} AlbRwbpWatchStatsV2;

#if defined(__cplusplus)
static_assert(sizeof(AlbRwbpWatchEventV2) == sizeof(AlbRwbpWatchEvent) + 56,
              "AlbRwbpWatchEventV2 的 v2 尾大小与内核 rwbp_trace_rec tail(8+8+4+4+32) 漂移了");
#elif defined(__STDC_VERSION__) && __STDC_VERSION__ >= 201112L
_Static_assert(sizeof(AlbRwbpWatchEventV2) == sizeof(AlbRwbpWatchEvent) + 56,
    "AlbRwbpWatchEventV2 的 v2 尾大小与内核 rwbp_trace_rec tail(8+8+4+4+32) 漂移了");
#endif

/* 模块是否具备 v2 通道（≥0.4.28）：1/0。每次调用探测（1 次 prctl，**不缓存**：
 * 模块可被后装载或原地升级，缓存负结果会让本进程永远按 v1 走）。 */
API int AlbRwbpWatchSupportsV2(void);
/* v2 使能（options 可为 NULL=全默认）。返回值/错误约定同 AlbRwbpWatchEnable：
 * >=0=环容量、<0=-errno；旧模块 + v2 专属选项 = -ENOTSUP，无 v2 选项则退化
 * 为 v1 会话（成功）。 */
API int AlbRwbpWatchEnableV2(const AlbRwbpWatchEnableOptions *options);
/* 取 v2 事件（含 v1 前缀，字段用法同 AlbRwbpWatchDrain）。
 * 返回取到的事件数；max_events 为数组容量。 */
API int AlbRwbpWatchDrainV2(AlbRwbpWatchEventV2 *events, int max_events);
/* v2 状态（含盲窗台账）。out 不能为 NULL。返回 >=0 = 当前可读条数（pending）；
 * <0 = -errno（模块不在位为负）。模块 ≤0.4.27 时 v2 字段全 0。 */
API int AlbRwbpWatchStatusV2(AlbRwbpWatchStatsV2 *out);

/* ---- rwbp 事件 journal（文件落盘，agent 侧）----
 * 把 drain 循环 + NDJSON 落盘 + 周期台账写进框架：内部起一个线程，drain 到底、
 * 逐条写 NDJSON 事件行，并按 status_interval_ms 写 status 行。等价于 doubao 侧
 * prybar 探针的 journal 逻辑（字段名与之对齐，宿主机工具可直接复用）。
 *
 * 单会话语义：与 AlbRwbpWatchGuardStartSampling / AlbRwbpWatchProbe 互斥（同一
 * 时刻只允许一个 drainer；Start 会先停掉 Guard 采样，与 Enable 的既有行为一致；
 * journal 期间 Probe 返回 -EBUSY）。Stop 只停 journal 线程并补一次末次排空，
 * **不释放会话/事件环**——释放仍走 AlbRwbpWatchStop（它也会顺带停 journal）。
 *
 * NDJSON 行：
 *   事件行 {"t":<ms>,"seq":...,"kind":...,"w":0/1,"far":"0x..","pc":"0x..",
 *           "lr":"0x..","sp":"0x..","tid":...,"ra":"0x..","rl":...,"r":[x0..x30]
 *           [,"t_ns":...,"t_real":...,"df":...,"dl":...,"data":"<hex>"]}
 *     （方括号内为 v2 会话（模块 ≥0.4.28）才有的尾部字段；t 为落盘点
 *      CLOCK_MONOTONIC ms，t_ns 为内核 fault 记录时刻，同钟可直接互减）
 *   status 行 {"t":<ms>,"status":1,"written":...,"dropped":...,"pending":...,
 *             "capacity":...,"lines":...,[v2 台账: "filtered_pc"/"filtered_pid"/
 *             "win_opens"/"win_open_now"/"win_ns"/"uaccess_opens"]}
 */
enum {
    /* v2：跳过每记录数据快照（省一次页表遍历；仅 v2 会话） */
    ALB_RWBP_JOURNAL_F_NO_DATA = 0x1,
    /* 模块不支持 v2（<0.4.28）时直接失败（-ENOTSUP），不退化成 v1 会话 */
    ALB_RWBP_JOURNAL_F_REQUIRE_V2 = 0x2,
};

typedef struct AlbRwbpWatchJournalOptions {
    const char *path;           /* NDJSON 输出路径（必填；覆盖写，Start 时打开） */
    unsigned int ring_pages;    /* 0 = 模块默认 */
    unsigned int flags;         /* ALB_RWBP_JOURNAL_F_* */
    unsigned long pc_start;     /* PC 包含过滤 [start,end)（仅 v2；0,0 = 关） */
    unsigned long pc_end;
    int status_interval_ms;     /* status 行间隔；<=0 取默认 2000ms */
} AlbRwbpWatchJournalOptions;

typedef struct AlbRwbpWatchJournalStats {
    unsigned long long lines;       /* 已写行数（事件行 + status 行） */
    unsigned long long events;      /* 其中事件行数 */
    unsigned long long bytes;       /* 已写字节数（行长度累加，近似） */
    unsigned long long dropped;     /* 末次 status 读到的环内丢弃 */
    unsigned long long filtered_pc; /* 末次 status 读到的 v2 PC 过滤计数（v1/旧模块恒 0） */
    unsigned long long win_opens;   /* 末次 status 读到的 v2 盲窗打开计数 */
    int v2;                         /* 本会话是否 v2 */
    int running;                    /* journal 线程是否在跑 */
} AlbRwbpWatchJournalStats;

/* 启动 journal。返回 >=0 = 事件环容量；<0 = -(errno)（-EINVAL 参数非法，
 * -EBUSY 已有 journal 在跑，-ENOTSUP REQUIRE_V2 但模块旧，其余为 enable 错误）。
 * 打开文件失败时不触碰会话；enable 失败按 AlbRwbpWatchEnableV2 语义处理
 * （通道会被停掉，旧会话不恢复）。 */
API int AlbRwbpWatchJournalStart(const AlbRwbpWatchJournalOptions *options);
/* 停线程 + 末次排空；返回 1 = 停了，0 = 本来没跑。会话/事件环不释放。 */
API int AlbRwbpWatchJournalStop(void);
/* 读统计（out 不能为 NULL）。返回 0；未运行时字段清零、running=0。 */
API int AlbRwbpWatchJournalStatus(AlbRwbpWatchJournalStats *out);

/* 盲区探针：把 address 处 size 字节**原样写回**一次（no-op 写，只由本进程线程
 * 执行），再把通道里已就绪的事件取到 events（等价一次 drain，调用方照常处理，
 * 计入 drained_out）。判定：
 *   1 = 捕获到本次探针的写事件 → 页面当时受保护，投递正常；
 *   0 = 未捕获 → 页面当时处于 grace 盲区（该会话可能漏事件，热页必须按此判据
 *       采样并告警）；
 *  <0 = -errno。
 * 原理：探针访问走目标进程自己的页表，页面受保护时必然触发断点；模块在
 * fault 路径里就写记录，所以"没事件"就等于"这次访问没被陷阱"。
 * 注意：会消费事件（请把取到的照常处理）；wait_ms 用于在 grace 窗口内等待，
 * 期间会推进时间使过期的页面重新受保护。 */
API int AlbRwbpWatchProbe(void *address, unsigned long size, int wait_ms,
                          AlbRwbpWatchEvent *events, int max_events, int *drained_out);

/* 探针采样统计 + 阈值告警。
 * 不额外起线程：调用方按自己的节奏（例如每次 drain 之后、或每秒一次）调用
 * AlbRwbpWatchProbe，这里只做累计与判定；连续判盲达到阈值即把该会话标记为
 * 不可信（reliable=0），据此在结果里给出告警，避免把"静默丢事件"当成
 * "没人访问过这个字段"。 */
#define ALB_RWBP_GUARD_DEFAULT_MISS 3

typedef struct AlbRwbpWatchGuardStats {
    unsigned long probes;             /* 采样次数 */
    unsigned long blind;              /* 判盲次数 */
    unsigned long consecutive_blind;  /* 最近连续判盲次数 */
    int miss_threshold;               /* 连续多少次判盲视为不可信 */
    int reliable;                     /* 1=至今未超阈值；0=会话不可信 */
} AlbRwbpWatchGuardStats;

/* 重置统计并设定阈值（miss_threshold<=0 用默认值） */
API void AlbRwbpWatchGuardReset(int miss_threshold);
API void AlbRwbpWatchGuardGet(AlbRwbpWatchGuardStats *out);

/* 定时采样（agent 侧用）：内部起一个线程，每 interval_ms 探针一次（自己 drain，
 * 事件转存内部缓冲），调用方照常用 AlbRwbpWatchDrain 取事件——采样期间由采样
 * 线程独占 drain，手动的 AlbRwbpWatchProbe 会返回 -EBUSY。
 * interval_ms<=0 取默认 100ms。返回 0 或 -errno。 */
API int AlbRwbpWatchGuardStartSampling(void *address, unsigned long size, int interval_ms);
API int AlbRwbpWatchGuardStopSampling(void);

API int AlbatrossRegisterRpcServer(void *server, void *address, const ArgType *args, unsigned char argCount,
                                   ArgType ret, const char *callName, int overwrite);

/* 取当前进程的默认 RPC server(注入进目标进程时即注入侧那个 server)。
 * 只有注入进程里有, 其它场景(宿主程序、根服务进程)返回 NULL。
 * 用途: 让拿不到宿主 entry 参数 server 的代码(CModule 脚本、hook 回调)也能
 * 注册自己的 RPC 方法: AlbatrossRegisterRpcServer(AlbatrossGetRpcServer(), ...)。 */
API void *AlbatrossGetRpcServer(void);

/* CModule: 进程内 tcc 动态编译 C 源码(类似 frida CModule)。
 * 编译出的代码可直接调用本头文件的全部 API 与常见 libc 函数。 */
typedef struct AlbCModule AlbCModule;

/* 编译 C 源码。options 为附加 tcc 选项(如 "-DFOO=1"), 传 NULL 即默认;
 * symbol_names/symbol_addrs 为注入的外部符号(源码里 extern 声明直接引用),
 * 可为空; 失败返回 NULL 并把错误文本(截断)写入 error。
 * 编译缓存: 相同 (源码, 选项, 注入符号表) 返回同一共享句柄(引用计数),
 * 每次成功编译都要对应一次 Free。
 * 生命周期回调(可选, 对齐 frida CModule 的 init/finalize, 都是"按模块一次"):
 * 源码定义 int alb_module_init(void) 则在重定位成功后、本函数返回前调用,
 * 返回非 0 判加载失败(返回 NULL, error 带返回码, 模块销毁);
 * 源码定义 void alb_module_fini(void) 则在最后一个引用释放、代码页销毁前
 * 调用一次(init 失败的销毁路径也会调, 用于回收 init 的半成品)。
 * 注意: 两个回调都在编译锁内执行, 回调里不得再编译/释放模块(非递归锁, 自死锁)。 */
API AlbCModule *AlbatrossCompileCModule(const char *source, const char *options,
                                        const char **symbol_names, void **symbol_addrs, int symbol_count,
                                        char *error, int error_size);

/* 取 CModule 内定义的符号(函数或全局变量)地址, 不存在返回 NULL。
 * 注意: 释放模块前必须先卸载 C 侧安装的 hook(或让脚本用 alb_module_fini
 * 在卸载回调里自行清理, 见 AlbatrossCompileCModule)。 */
API void *AlbatrossCModuleGetSymbol(AlbCModule *module, const char *name);

/* 释放一次 CModule 引用; 缓存共享模块在最后一个引用释放时才 tcc_delete。
 * 最后一个引用释放时会做一次引用审计(AlbatrossCModuleAudit): 若代码页里
 * 仍有被引用的 hook 回调 / 延迟 hook / 已注册 RPC, 会经 AlbLog 打出告警
 * (不阻止释放也不自动卸载, 残留引用随后即悬空)。 */
API void AlbatrossCModuleFree(AlbCModule *module);

/* ---- CModule 自省 / 调试(便于 AI/工具离线归因, 见 skill runtime-rpc.md) ---- */

/* 模块台账文本: 头部一行(cmodule#id handle=0x.. src=哈希 refs=.. cached=..
 * note=".." range=..)+ 代码页区间 + 导出符号表(name +0x偏移); 尾部在存在
 * 残留引用时附 audit 明细。写不下会截断(总长可远大于 1KB, 传足量缓冲)。
 * 返回写入的字节数(不含结尾 NUL); 缓冲不足时返回需要的长度。 */
API int AlbatrossCModuleDescribe(AlbCModule *module, char *out, int out_size);

/* 存活模块清单: 最多填 max 个句柄进 out(max<=0 表示只数不填), 返回存活模块
 * 总数(可能大于 max)。用于跨会话盘点"进程里现在有哪些脚本"。
 * 注意: 句柄的生存期由调用方负责(拿到后别跨线程/跨时刻直接 Describe——
 * 要"一次拿全量台账"用 AlbatrossCModuleDescribeAll)。 */
API int AlbatrossCModuleList(AlbCModule **out, int max);

/* 全部存活模块的台账(每模块一行头部, 与 AlbatrossCModuleDescribe 首行同格式),
 * 单次持锁完成——避免"List 拿句柄 → 逐个 Describe"之间模块被并发释放。 */
API int AlbatrossCModuleDescribeAll(char *out, int out_size);

/* 模块源码副本(编译时保存; 释放前有效), 无源码返回 NULL。 */
API const char *AlbatrossCModuleGetSource(AlbCModule *module);

/* 导出符号表文本("name +0xoffset" 每行一个); 返回写入字节数。 */
API int AlbatrossCModuleGetSymbols(AlbCModule *module, char *out, int out_size);

/* 内置能力清单文本: 构建模式/入口约定/虚拟头清单/框架 API 名/内置 libc 名。
 * 供脚本与工具查询"哪些符号可以直接用", 不用猜。返回写入字节数。 */
API int AlbatrossCModuleGetCapabilities(char *out, int out_size);

/* 模块备注(属主/用途), 用于多会话/多 agent 并发时互相识别; NULL 清除。 */
API void AlbatrossCModuleSetNote(AlbCModule *module, const char *note);
API const char *AlbatrossCModuleGetNote(AlbCModule *module);

/* 地址 -> "cmodule#id 符号+0x偏移" 文本(addr 落在某个存活模块的代码页区间内
 * 时)。tombstone/backtrace 里的 PC 可以拿它离线归因到脚本符号。
 * 返回 1 = 命中(文本已写), 0 = 不在任何模块内。 */
API int AlbatrossCModuleSymbolize(void *addr, char *out, int out_size);

/* 引用审计: 检查模块代码页内的地址是否仍被 hook 回调 / 延迟 hook / 已注册
 * RPC 方法引用, 返回位掩码(见 ALB_CMODULE_REF_*), 明细文本(可空)写 out。 */
#define ALB_CMODULE_REF_HOOK 1u     /* 替换式/插桩式 hook 的回调或 listener */
#define ALB_CMODULE_REF_DELAYED 2u  /* 延迟 hook(按库/偏移)条目的回调 */
#define ALB_CMODULE_REF_RPC 4u      /* 脚本经 AlbatrossRegisterRpcServer 注册的方法 */
API unsigned AlbatrossCModuleAudit(AlbCModule *module, char *out, int out_size);

/* 合作式取消/超时: 脚本在长循环里调用(无参数, 自动按返回地址归属到当前模块)。
 * 返回 0 = 继续跑; 1 = 该模块已被 AlbatrossCModuleCancel; 2 = 超过
 * AlbatrossCModuleSetDeadline 设定的死线。脚本应按返回值尽快退出(检查点式,
 * 框架不强制打断)。不在任何模块代码内调用返回 0。 */
API int AlbatrossCModuleCheckpoint(void);
API void AlbatrossCModuleCancel(AlbCModule *module);
/* ms_from_now <= 0 清除死线; 死线用单调钟, 进程内所有模块独立。 */
API void AlbatrossCModuleSetDeadline(AlbCModule *module, long ms_from_now);

/* 通用调用(供 RPC/宿主用): 按 argv 形态入口约定调用模块导出函数
 *   long entry(const unsigned long *argv, int argc, char *out, int out_size)
 * 约定: 传给入口的 out_size 是"可用字符数"(本函数传 out_size-1, 末尾由框架补
 * NUL) —— 脚本按 snprintf(out, out_size, ...) 写即可; out 传 NULL 时按 0 传。
 * 返回入口的机器字返回值; 入口不存在返回 -1(err 写原因)。
 * 与 cmodule_call_v 同语义(见 skill runtime-rpc.md)。 */
API long AlbatrossCModuleCall(AlbCModule *module, const char *entry,
                              const unsigned long *argv, int argc, char *out, int out_size,
                              char *error, int error_size);

/* 受守卫调用(供 RPC/宿主用): 按字符串入口约定调用模块导出函数
 *   usize entry(const char *arg, char *out, int out_size)
 * 入口约定与 cmodule_run/cmodule_call 一致(out_size 传"可用字符数", 末尾由
 * 框架补 NUL); 返回入口的机器字返回值, 入口不存在或脚本被守卫拦下时返回 -1
 * 并把原因写进 error。
 * "守卫"= 进程内故障兜底(与 AlbatrossCModuleSetRunTimeout 同一套机制): 脚本
 * 在调用期间触发 SIGSEGV/SIGBUS/SIGILL/SIGFPE/SIGABRT 时, 框架把它转成 error
 * 文本(信号名/故障地址/PC 符号化/寄存器快照)并返回 -1, 进程不崩 —— best-effort,
 * 边界语义见 doc/pitfalls/pitfall-cmodule-guard-longjmp-20261014.md。 */
API long AlbatrossCModuleInvoke(AlbCModule *module, const char *entry, const char *arg,
                                char *out, int out_size, char *error, int error_size);

/* 非合作式运行超时(毫秒, <=0 关闭): 对该模块**之后每次调用**生效。到点时由
 * 看门狗线程给被守卫线程递一个进程内信号, 守卫把这次调用按"运行超时"中断并
 * 返回错误(脚本不必自己调 Checkpoint)。与合作式的 AlbatrossCModuleSetDeadline
 * 互补: 死线要求脚本在长循环里主动查询, 本超时由框架强制打断(best-effort:
 * 跳出点可能在任意指令上, 跳出后模块标记 faulted 并从编译缓存剔除, 建议重新
 * 编译再跑)。cmodule_run / cmodule_run_isolated 由 RPC 侧默认设置(cmodule_run
 * 默认 30s), cmodule_call/cmodule_call_v 默认不设, 需要时经 cmodule_set_timeout
 * 或本 API 打开。 */
API void AlbatrossCModuleSetRunTimeout(AlbCModule *module, long ms);

/* 隔离执行(一次性脚本的"试跑"通道): fork 一个子进程, 在子进程里执行
 * alb_module_init(若有) -> entry(默认 alb_main) -> alb_module_fini(若有),
 * 父进程等它结束(带 timeout_ms 超时, 到点 SIGKILL 收尸)。
 *   - 脚本崩溃: 父进程不受影响, 返回 -1, error 带信号名/故障地址/PC 符号化;
 *   - 脚本死循环: 到时杀掉子进程, *timed_out 回填 1;
 *   - 副作用隔离: 脚本对内存的写入只落在子进程副本(模块代码页若为 memfd 共享
 *     映射, 也已在子进程内私有化), 父进程状态不变 —— 正因为如此, **装 hook /
 *     改目标内存这类脚本在隔离模式里不会对父进程生效**: 验证逻辑与查内存用隔离,
 *     要改状态用 AlbatrossCompileCModule + AlbatrossCModuleInvoke。
 * 父进程侧编译(编译缓存生效, 但不跑 init); 返回值 = 入口的机器字返回值, 失败
 * (编译失败/入口缺失/崩溃/超时)返回 -1。*run_ms 回填子进程实际运行毫秒,
 * *term_signal 回填死亡信号(0 = 正常退出), *timed_out 回填是否超时被杀; 三个
 * 出参都可传 NULL。timeout_ms <= 0 用默认值(30s)。
 * 约束: 子进程里脚本只应做"读/算"——父进程其它线程持有的锁不在子进程里复现,
 * 调框架 API 可能等锁到超时; fork 瞬间父进程其它线程持有分配器锁的极小窗口内
 * 子进程可能卡住, 表现为 timed_out 而非脚本问题, 重试即可。 */
API long AlbatrossCModuleRunIsolated(const char *source, const char *options,
                                     const char *entry, const char *arg, long timeout_ms,
                                     char *out, int out_size, char *error, int error_size,
                                     long *run_ms, int *term_signal, int *timed_out);

/* 内存区间可读/可写探测(免 ptrace)。
 * 整个 [address, address+size) 都满足权限才返回 1; 0 = 不可用/越界/参数非法。
 * 判定按"区间被满足权限的映射连续覆盖": 跨相邻同权限映射也算通过(不要求落在
 * 同一个映射内)。
 * 可读: 先走 process_vm_readv(读自身)逐页认证(一次系统调用, 页权限为准);
 *   区间超过 64 页或系统调用不可用时退回 /proc/self/maps。
 * 可写: 只有 /proc/self/maps 一条路 —— 没有"只验证不落笔"的写类系统调用
 *   (msync 各 flag/mincore/madvise 只证明已映射, 对 PROT_NONE/只读同样成功;
 *   process_vm_writev 非零长度即真写数据, 零长度 iovec 不做任何验证)。
 * 用途: 脚本对算出来的地址先验后用(装钩/读写内存前自检), 见 skill 的脚本模板。 */
API int AlbMemoryReadable(void *address, unsigned long size);
API int AlbMemoryWritable(void *address, unsigned long size);

/* 自检式内存读写(免 ptrace, 对自身地址空间): 走 process_vm_readv/writev 读/写
 * 本进程内存, **地址非法时返回 -1 而不是 SIGSEGV 崩进程** —— 脚本拿算出来的
 * 地址盲读写时, 用这两个函数代替裸指针解引用。
 * 成功返回读/写的字节数(等于 size), 失败返回 -1。对自身读写免 ptrace 权限;
 * 只读页写、未映射页读写都会失败返回(内核 EFAULT), 不会触碰目标内存。
 * 约束: 每次调用一次系统调用(单次约百纳秒级), 适合探针/补丁这类低频访问;
 * hook 热路径/性能敏感点仍用裸指针(配合 AlbMemoryReadable/Writable 先验后用)。
 * 与 process_vm_readv/writev 的关系: 这两个是它的别名封装(免构造 iovec)。 */
API long AlbMemoryRead(void *address, void *buffer, unsigned long size);
API long AlbMemoryWrite(void *address, const void *buffer, unsigned long size);


/*
 * Stalker: 动态代码追踪 (指令级重编译插桩)。对齐 frida 的 Stalker 模型:
 * 一个 tracker 句柄 + 可选的 transformer (改写被翻译的指令) + 可选的
 * event sink (接收执行事件)。
 *
 * 典型用法(从 CModule 或宿主 C 代码):
 *
 *   static volatile int g_execs;
 *   static void on_event(const AlbStalkerEvent *e, void *ud) {
 *     if (e->type == ALB_STALKER_EVENT_EXEC) g_execs++;
 *   }
 *   AlbStalkerHandle *s = AlbStalkerNew();
 *   AlbStalkerExcludeNamed(s, "jit-code-cache");   // 裸 follow 必做
 *   AlbStalkerEventSinkHandle *sink = AlbStalkerEventSinkNew(ALB_STALKER_EVENT_EXEC, on_event, 0);
 *   AlbStalkerFollowCurrentThread(s, 0, sink);      // 调用点之后的代码进入追踪
 *   ...被追踪的代码...
 *   AlbStalkerUnfollowCurrentThread(s);
 *   AlbStalkerEventSinkFree(sink);
 *   AlbStalkerFree(s);
 *
 * 约束(与 Java 暴露层一致):
 *  - 事件 / transformer / callout 回调都在被追踪线程同步执行, 除读写事件或
 *    寄存器外不要做重活, 也不要在回调里调用 stalker API。
 *  - 裸 follow 必须先 Exclude JIT 代码缓存(见上方示例): 其内容被运行时
 *    持续失效与地址复用, 翻译后重写执行必然崩溃。
 *  - 先 Unfollow 再 Free 句柄; FollowFunction 挂的 hook 要先
 *    AlbStalkerUnfollowFunction 卸载; CModule 里定义的回调函数在
 *    AlbatrossCModuleFree 后失效, 必须先结束追踪。
 *  - 编译路径假定不重入 libc 分配器: 跟踪范围建议用 Exclude/Include
 *    限定在目标代码内, 不要裸 follow 整个进程。
 */
typedef struct AlbStalkerHandle AlbStalkerHandle;
typedef struct AlbStalkerTransformerHandle AlbStalkerTransformerHandle;
typedef struct AlbStalkerEventSinkHandle AlbStalkerEventSinkHandle;
/* transformer 回调内传给用户的迭代器句柄 */
typedef struct AlbStalkerIteratorHandle AlbStalkerIteratorHandle;
/* callout 回调内传给用户的寄存器现场句柄 */
typedef struct AlbStalkerContextHandle AlbStalkerContextHandle;

/* 事件掩码, 与内部事件类型一一对应 */
enum {
    ALB_STALKER_EVENT_CALL = 1 << 0,
    ALB_STALKER_EVENT_RET = 1 << 1,
    ALB_STALKER_EVENT_EXEC = 1 << 2,
    ALB_STALKER_EVENT_BLOCK = 1 << 3,
    ALB_STALKER_EVENT_COMPILE = 1 << 4,
};

/* 事件, 布局与内部 AlbEvent 一致 */
typedef struct AlbStalkerEvent {
    unsigned int type;
    union {
        struct {
            unsigned long location;
            unsigned long target;
            unsigned int depth;
        } call;
        struct {
            unsigned long location;
            unsigned long target;
            unsigned int depth;
        } ret;
        struct {
            unsigned long location;
            /* 块编译期算出的本指令读/写 GPR 掩码(槽位序见框架内 default_trace.h),
             * 值在事件回调的 cpu_context 里按槽位自取; 不需要寄存器可忽略。
             * 只能追加在 location 之后(与内部 AlbExecEvent 布局互锁) */
            unsigned int regs;
        } exec;
        struct {
            unsigned long start;
            unsigned long end;
        } block;
        struct {
            unsigned long start;
            unsigned long end;
        } compile;
    };
} AlbStalkerEvent;

/* 迭代器回吐的指令信息(id 为架构相关 opcode: X86_INS_* / ARM64_INS_* 等) */
typedef struct AlbStalkerInstruction {
    unsigned long address;
    unsigned int size;
    unsigned int id;
} AlbStalkerInstruction;

/*
 * transformer 回调: 逐条遍历被翻译块里的指令, 对每条指令调用
 * AlbStalkerIteratorKeep 保留, 不 keep 即跳过(如筛除某个 call)。
 * 遍历结束由框架隐式处理, 用户不必也无需调用 next 到返回 0 之外的操作。
 */
typedef void (*AlbStalkerTransformFunc)(AlbStalkerIteratorHandle *iterator,
                                        void *user_data);

/* callout 回调: 在指定指令执行前插入的 C 回调, 可读写寄存器 */
typedef void (*AlbStalkerCalloutFunc)(AlbStalkerContextHandle *context,
                                      void *user_data);

/* 事件回调: 在被追踪线程同步调用 */
typedef void (*AlbStalkerEventFunc)(const AlbStalkerEvent *event,
                                    void *user_data);

/* 生命周期: 有活动会话时 Free 返回非 0 并保持句柄有效 */
API AlbStalkerHandle *AlbStalkerNew(void);
API int AlbStalkerFree(AlbStalkerHandle *stalker);

/* transformer 句柄; callback 为 NULL 时使用默认行为(保留全部指令) */
API AlbStalkerTransformerHandle *AlbStalkerTransformerNew(
    AlbStalkerTransformFunc callback, void *user_data);
API void AlbStalkerTransformerFree(AlbStalkerTransformerHandle *transformer);

/* 事件 sink 句柄; event_mask 为 ALB_STALKER_EVENT_* 的按位或 */
API AlbStalkerEventSinkHandle *AlbStalkerEventSinkNew(unsigned int event_mask,
                                                      AlbStalkerEventFunc callback, void *user_data);
API void AlbStalkerEventSinkFree(AlbStalkerEventSinkHandle *sink);

/* 从当前线程开始追踪(调用点之后的执行进入追踪), 异步返回 */
API void AlbStalkerFollowCurrentThread(AlbStalkerHandle *stalker,
                                       AlbStalkerTransformerHandle *transformer, AlbStalkerEventSinkHandle *sink);
API void AlbStalkerUnfollowCurrentThread(AlbStalkerHandle *stalker);

/* 追踪指定线程 */
API int AlbStalkerFollow(AlbStalkerHandle *stalker, int thread_id,
                         AlbStalkerTransformerHandle *transformer, AlbStalkerEventSinkHandle *sink);
API void AlbStalkerUnfollow(AlbStalkerHandle *stalker, int thread_id);

/*
 * 便捷入口: 挂住目标函数, 每次进入自动 follow 当前线程、退出自动 unfollow,
 * 从而追踪该函数的完整执行(含它调用的所有代码)。返回 0 成功。
 * 嵌套调用安全; instrument_flags 见 InstrumentFlags。
 *
 * 配对按线程记账(递归安全; 多线程同时进入目标函数时每个线程都被独立 follow,
 * 事件回调可能并发, 回调需自行保证线程安全)。exec ctx 按会话池化复用:
 * unfollow 归还、下次 follow 直接复用(块缓存保留), 超热函数(每秒数万次调用,
 * 含分配器路径上的 libc 函数)可承受 —— 不再是"每次调用重建兆级上下文"。
 * 跟踪范围仍建议用 Exclude/Include 限定: 裸追整个进程的事件量没有意义。
 */
API int AlbStalkerFollowFunction(AlbStalkerHandle *stalker, void *function,
                                 int instrument_flags, AlbStalkerTransformerHandle *transformer,
                                 AlbStalkerEventSinkHandle *sink);

/* 卸载一个 AlbStalkerFollowFunction 挂上的函数; 返回 0 成功 */
API int AlbStalkerUnfollowFunction(AlbStalkerHandle *stalker, void *function);

/* 坑见 doc/pitfalls/pitfall-trace-window-timeout-20261004.md（默认 60s 兜底 / 只 disarm 不 unhook）
 *
 * ---- 追踪窗口控制: 超时 / 事件数上限 ----
 *
 * 默认兜底: 未调用 AlbStalkerSetTimeout 的会话在 60s 后自动停止, 防止"忘了停"
 * 的追踪一直占用目标进程; 显式传 0 表示不限时。
 *
 * 计时窗口从**第一次真正 follow** 起算(followFunction 即目标函数第一次被调用的
 * 时刻), 不是从调用本函数起算; 目标函数始终没被调用就一直不计时, 超时自然也不
 * 会触发。触发后框架只做两件事: 停止事件流(解除 followFunction 的自动 follow +
 * unfollow 所有活跃线程)并记录停止原因; **不会** 卸载 FollowFunction 挂上的 hook
 * (目标函数执行中 unhook 不受支持), 彻底撤销仍用 AlbStalkerUnfollowFunction。
 *
 * 想再追一轮: 重新 FollowFunction/Follow(显式 follow = 新会话, 会清掉上一次的停止
 * 状态并按当前 timeout_ms/max_events 重新开窗), 或先调 AlbStalkerSetTimeout 改配置。
 *
 * max_events 是软上限: watchdog 每 5ms 检查一次, 实际可能略超。
 */
enum {
    ALB_STALKER_STOP_NONE = 0,
    ALB_STALKER_STOP_TIMEOUT = 1,
    ALB_STALKER_STOP_MAX_EVENTS = 2,
};

/* 超时毫秒数: >0 生效, 0 不限时; 可在 follow 前设置, 也可在会话中调用(立即开新窗口) */
API int AlbStalkerSetTimeout(AlbStalkerHandle *stalker, int timeout_ms);
/* 事件数上限: 抓到 max_events 条事件即停(0 = 不限, 默认) */
API int AlbStalkerSetMaxEvents(AlbStalkerHandle *stalker, unsigned long max_events);
/* 停止原因(ALB_STALKER_STOP_*): 用户 unfollow / 目标线程退出后保持 NONE */
API int AlbStalkerGetStopReason(AlbStalkerHandle *stalker);
/* 本次窗口内已投递的事件数(窗口开始/重新开窗时清零) */
API unsigned long AlbStalkerGetEventCount(AlbStalkerHandle *stalker);

/* 过滤: exclude 的区间不被翻译(直接执行原代码), include 是白名单 */
API void AlbStalkerExcludeRange(AlbStalkerHandle *stalker, void *base,
                                unsigned long size);

/* 排除/包含一个模块的可执行段(name 为模块路径, 子串匹配) */
API void AlbStalkerExcludeModule(AlbStalkerHandle *stalker, const char *name);
API void AlbStalkerIncludeModule(AlbStalkerHandle *stalker, const char *name);

/* 按名称子串匹配可执行区间(文件映射路径或匿名区间名, 如
 * "jit-code-cache"), 只作用于可执行段 */
API void AlbStalkerExcludeNamed(AlbStalkerHandle *stalker,
                                const char *name_substr);
API void AlbStalkerIncludeNamed(AlbStalkerHandle *stalker,
                                const char *name_substr);

API void AlbStalkerClearExclusions(AlbStalkerHandle *stalker);
API void AlbStalkerClearInclusions(AlbStalkerHandle *stalker);

/* transformer 回调内: 取下一块内指令, 返回 0 表示块结束 */
API int AlbStalkerIteratorNext(AlbStalkerIteratorHandle *iterator,
                               AlbStalkerInstruction *instruction);
API void AlbStalkerIteratorKeep(AlbStalkerIteratorHandle *iterator);
/* 在当前指令前插入 callout(如读改寄存器) */
API void AlbStalkerIteratorPutCallout(AlbStalkerIteratorHandle *iterator,
                                      AlbStalkerCalloutFunc callback, void *user_data);

/* callout 回调内: 按名读写寄存器(x0-x30/fp/lr/sp/pc/nzcv, 见平台文档),
 * 返回 0 成功 */
API int AlbStalkerContextGetRegister(AlbStalkerContextHandle *context,
                                     const char *name, unsigned long *value);
API int AlbStalkerContextSetRegister(AlbStalkerContextHandle *context,
                                     const char *name, unsigned long value);
/* 第 nth 个函数参数(前 8 个走寄存器, 其余走栈); 写回走各架构原生位置
 * (寄存器架构写参数寄存器, i386 写栈参)——比按名写寄存器跨架构可移植 */
API unsigned long AlbStalkerContextGetArgument(AlbStalkerContextHandle *context,
                                               int nth);
API int AlbStalkerContextSetArgument(AlbStalkerContextHandle *context,
                                     int nth, unsigned long value);

/* 信任阈值: -1 每次校验快照(检测自修改代码, 最慢), 0 不保留快照,
 * >=1 块复用该次数后免检(默认 1) */
API int AlbStalkerGetTrustThreshold(AlbStalkerHandle *stalker);
API void AlbStalkerSetTrustThreshold(AlbStalkerHandle *stalker, int threshold);

/* 使目标地址所在的块失效并重编译(如代码被运行时改写后) */
API void AlbStalkerInvalidate(AlbStalkerHandle *stalker, void *address);

/* 回收已退出线程的追踪上下文; 返回非 0 表示仍有未回收的上下文 */
API int AlbStalkerGarbageCollect(AlbStalkerHandle *stalker);


/*
 * 内置默认 native trace(等价 Java 的 AlbNative.stalkerTraceFunction 一族):
 * 追踪单个 native 函数的运行过程(call/ret/exec/block + 寄存器 + 可选访存字节),
 * 记进定长环形缓冲, 结束后 Dump 成文本/落盘。进程内单会话, 与 Java 面共用同一
 * 个会话槽(混用同一槽位, 换目标互相被拒)。
 *
 * 用法(从 CModule 或宿主 C 代码):
 *
 *   char buf[8192];
 *   if (AlbStalkerTraceStart("libtarget.so", "encrypt", 0, 30000, 0, 0,
 *           "/data/local/tmp/t.log") == 0) {
 *     trigger_the_target();                  // 触发目标执行
 *     AlbStalkerTraceStop();                 // 停窗口 + 落盘
 *   }
 *   AlbStalkerTraceDump(buf, sizeof(buf));   // 文本(符号名/寄存器/访存字节)
 *   AlbStalkerTraceRelease();                // 目标已静止: 释放, 之后可换目标
 *
 * 参数: address != 0 时直接追踪该地址(lib/symbol 忽略); 否则按 lib 名子串 +
 * 符号名解析(先框架 dlopen/dlsym, 再已加载模块表, 最后全模块符号扫描)。
 * timeout_ms < 0 = 默认 60s 兜底, 0 = 不限时; max_events 0 = 不限;
 * log_path 为空/NULL = 不落盘(只能 Dump 取)。flags 见下方常量。
 *
 * 延迟 trace(ALB_STALKER_TRACE_FLAG_DELAYED): 目标库尚未加载时先登记, 库被
 * dlopen 加载后自动解析并开始(安装发生在 dlopen 返回其调用方之前); 等待无限期,
 * 期间状态 PENDING, Stop/Release 取消。库已加载但符号暂缺时保持等待(重试解析
 * 只按 lib 名匹配, 不做全模块符号扫描); 库已加载且可解析时立即开始, 与不传
 * DELAYED 等价。延迟要求 lib 名(或 offset), 地址已给定时无延迟语义。
 *
 * 单会话语义: 同目标重复 Start = 更新参数/重开窗口(运行中则幂等返回 0); 换目标
 * 返回 ERR_BUSY, 直到 Release 释放 —— enter/leave 钩子在释放前一直保留(目标
 * 执行中 unhook 不受支持), 因此 Release 的契约是目标已静止。解析到目标但安装
 * 失败时状态停在 FAILED(无记录), 可再次 Start 重试(同/异目标皆可)。
 */
enum {
    /* 只记录主线程(其余线程进入目标函数不触发追踪) */
    ALB_STALKER_TRACE_FLAG_MAIN_THREAD_ONLY = 0x1,
    /* 不记录访存指令读/写的数据(默认记录: 读侧到访问地址读内存, 写侧从源
     * 寄存器/立即数重建, 见 default_trace.h 的 ALB_TRACE_REC_MEM) */
    ALB_STALKER_TRACE_FLAG_MEM_BYTES_OFF = 0x2,
    /* 目标库未加载时先登记, 加载后自动开始(等待无限期, Stop/Release 取消) */
    ALB_STALKER_TRACE_FLAG_DELAYED = 0x4,
};

/* 会话状态(AlbStalkerTraceState) */
enum {
    ALB_STALKER_TRACE_NONE = 0,    /* 无会话 */
    ALB_STALKER_TRACE_PENDING = 1, /* DELAYED 已登记, 等待目标库加载 */
    ALB_STALKER_TRACE_RUNNING = 2, /* 追踪窗口进行中 */
    ALB_STALKER_TRACE_STOPPED = 3, /* 已停止(记录仍可 Dump/落盘) */
    ALB_STALKER_TRACE_FAILED = 4,  /* 延迟登记最终失败(解析到目标但安装失败), 无记录 */
};

/* AlbStalkerTraceStart 的返回: 0 = 已开始, 1 = 已登记等待(DELAYED), 负数 = 错误 */
enum {
    ALB_STALKER_TRACE_ERR_ARG = -1,       /* 参数非法(目标为空/offset 为 0 等) */
    ALB_STALKER_TRACE_ERR_NOT_FOUND = -2, /* 非延迟: 目标解析不到 */
    ALB_STALKER_TRACE_ERR_START = -3,     /* 解析到目标但启动失败(过滤/引擎/装钩) */
    ALB_STALKER_TRACE_ERR_BUSY = -4,      /* 单会话: 已有会话且目标不同 */
    ALB_STALKER_TRACE_ERR_TRIGGER = -5,   /* DELAYED 但装不上库加载触发器(无 libc/linker 环境) */
    ALB_STALKER_TRACE_ERR_MEMORY = -6,    /* 内存不足(登记条目/落盘路径分配失败) */
};

/* 启动/登记; 返回 0 已开始 / 1 已登记等待 / 负错误码(见上) */
API int AlbStalkerTraceStart(const char *lib_name, const char *symbol_name,
                             void *address, int timeout_ms, unsigned long max_events, unsigned int flags,
                             const char *log_path);

/* 按模块内偏移启动/登记: offset 为 ELF 虚拟地址(IDA/readelf 里的 vaddr), 实际
 * 地址 = 模块基址 + offset; 给符号拿不到(未导出/被裁剪)的目标用。其余语义同上 */
API int AlbStalkerTraceStartOffset(const char *lib_name, unsigned long offset,
                                   int timeout_ms, unsigned long max_events, unsigned int flags,
                                   const char *log_path);

/* 停止窗口 / 取消延迟登记, 并落盘(已停未落盘时补写); 返回 1 = 有动作, 0 = 无 */
API int AlbStalkerTraceStop(void);

/* 彻底释放会话(含取消延迟登记), 之后可换目标; 返回 1 = 释放了会话/登记, 0 = 无。
 * 契约: 目标函数已静止(不在执行中)—— 释放会卸载其 enter/leave 钩子 */
API int AlbStalkerTraceRelease(void);

/* 状态(ALB_STALKER_TRACE_*); 停止原因(ALB_STALKER_STOP_*, 无会话返回 -1) */
API int AlbStalkerTraceState(void);
API int AlbStalkerTraceGetStopReason(void);

/* 状态 JSON(running/started/pending/target/lib_path/log_path/stop_reason/...),
 * 与 Java 面同一份。写入调用方缓冲(NUL 结尾; 放不下则截断但保证终止), 返回应有
 * 长度(不含 NUL): 返回值 >= size 表示被截断, 用更大缓冲重试。buffer 为 NULL 或
 * size 为 0 时只返回长度 */
API int AlbStalkerTraceStatus(char *buffer, unsigned long size);

/* 格式化 trace 文本(追踪过程, 语义同 Java 的 stalkerTraceDump); 缓冲约定同上;
 * 未启动/无记录时写入空串并返回 0 */
API int AlbStalkerTraceDump(char *buffer, unsigned long size);


/* 日志优先级, 取值与 android_LogPriority 一致 */
enum {
    ALB_LOG_VERBOSE = 2,
    ALB_LOG_DEBUG = 3,
    ALB_LOG_INFO = 4,
    ALB_LOG_WARN = 5,
    ALB_LOG_ERROR = 6,
    ALB_LOG_FATAL = 7,
};

/* 日志输出: Android 走 logcat, 其余走 stdout, 自动换行。 */
API void AlbLog(const char *tag, const char *fmt, ...);

/* 同 AlbLog, 可指定优先级(ALB_LOG_*); 非 Android 平台忽略优先级。 */
API void AlbLogPrio(int prio, const char *tag, const char *fmt, ...);

/* 插件 ABI 表版本。
 *
 * 规则:向 AlbatrossApi 追加任何字段,都必须同时 bump 这个版本号,并把
 * ALBATROSS_API_TABLE_POINTER_FIELDS 改成新的字段个数(改了布局却没改这个常数时,
 * 下面的 static_assert 会直接编译失败,防止再次漂移)。
 *
 * 为什么必须 bump:老库的表更短,插件读尾部字段会静默拿到垃圾指针(真机崩过一次,
 * 见 doc/framework_feedback/bug-api-table-struct-size-20261003.md);版本号 + 字段数
 * 是插件在 entry 里"明确拒绝老库"的依据,写法见 AlbatrossApiCheck()。
 *
 * v1: 早期表(字段到 register_api 为止);v2: 追加 CModule / Stalker / 库加载回调 /
 *     延迟 hook / get_module_base 之后的现表;v3: 追加 android_load_jvm;
 *     v4: 追加 stalker_set_timeout / set_max_events / get_stop_reason / get_event_count;
 *     v5: 追加 target_uses_indirect_return;
 *     v6: 追加内置默认 trace 一族(AlbStalkerTrace*);
 *     v7: 追加 rwbp watch 一族 + journal(AlbRwbpWatch* / AlbRwbpWatchJournal*);
 *     v8: 追加 PLT hook 一族(AlbPltHook* / AlbPltUnhook / AlbPltAddIgnore)。
 */
enum {
    ALBATROSS_API_TABLE_VERSION = 8,
};

/* 本版本表的"指针字段"个数;静断用「2 个 u32 + N 个指针」表达,与 32/64 位无关 */
#define ALBATROSS_API_TABLE_POINTER_FIELDS 113

/*
 * Stable plugin ABI. Keep the existing field order and only append new fields.
 * 追加字段 = bump ALBATROSS_API_TABLE_VERSION + 更新 ALBATROSS_API_TABLE_POINTER_FIELDS
 * (漏改会被下面的静态断言拦下)。兼容性判定用 AlbatrossApiCheck / ALBATROSS_API_HAS_FIELD,
 * 不要用 "abi_version == ALBATROSS_API_TABLE_VERSION":表只追加,新库配老插件是合法的。
 */
typedef struct AlbatrossApi {
    unsigned int abi_version;
    unsigned int struct_size;

    int (*get_version)(void);

    void *(*dlsym)(void *handle, const char *name);

    void *(*dlopen)(int pid, const char *libpath, int flags);

    void *(*dlopen_self)(const char *libpath, int flags);

    int (*dlclose)(void *handle);

    void *(*iter_symbol)(void *handle, search_func func, void *data);

    void (*enumerate_modules)(int pid, search_func callback, void *data);

    int (*transaction_begin)(void);

    int (*transaction_end)(void);

    void (*transaction_force_end)(void);

    int (*hook_func)(void *address, void *replace_func, void **origin_func);

    void *(*get_backup)(void *address);

    int (*unhook)(void *address);

    long (*hook_instrument)(void *address, enter_listener on_enter,
                            leave_listener on_leave, DetachCallback on_detach, void *user_data,
                            int instrument_flags);

    int (*unhook_instrument)(void *address, long listener_id);

    unsigned long (*get_nth_argument)(InvocationContext *invocation_context, int nth);

    unsigned long (*get_reg_value)(InvocationContext *invocation_context, int reg);

    unsigned long (*get_result)(InvocationContext *invocation_context);

    unsigned long (*get_sp)(InvocationContext *invocation_context);

    void (*set_nth_argument)(InvocationContext *invocation_context, int nth,
                             unsigned long value);

    void (*set_reg_value)(InvocationContext *invocation_context, int reg,
                          unsigned long value);

    void (*set_return_result)(InvocationContext *invocation_context,
                              unsigned long long value);

    void (*set_user_data)(InvocationContext *invocation_context, void *data);

    void *(*get_user_data)(InvocationContext *invocation_context);

    void *(*get_func)(InvocationContext *invocation_context);

    void (*destroy)(void);

    int (*android_init)(void *env, void *albatross_class);

    int (*ocean_init)(void *env, void *ocean_tracer);

    int (*android_init_native)(void *env, void *art_handler, int method_count);

    int (*android_load_dex)(const char *dex_path, const char *lib_path,
                            const char *register_class, const char *class_name, int flags,
                            const char *loader_symbol_name, const char *param1, int param2);

    void *(*get_current_jni_env)(void);

    WatchHandler *(*watch_memory)(void *address, unsigned long memory_size,
                                  void *userdata, AlbPageProtection access_mask, WatchCallback callback);

    void (*watch_remove)(WatchHandler **context);

    int (*register_api)(void *server, void *address, const ArgType *args, unsigned char argCount,
                        ArgType ret, const char *callName, int overwrite);

    unsigned long (*get_named_reg_value)(InvocationContext *invocation_context,
                                         const char *reg_name);

    void (*set_named_reg_value)(InvocationContext *invocation_context,
                                const char *reg_name, unsigned long value);

    AlbCModule *(*compile_cmodule)(const char *source, const char *options,
                                   const char **symbol_names, void **symbol_addrs, int symbol_count,
                                   char *error, int error_size);

    void *(*cmodule_get_symbol)(AlbCModule *module, const char *name);

    void (*cmodule_free)(AlbCModule *module);

    void (*log)(const char *tag, const char *fmt, ...);

    void (*log_prio)(int prio, const char *tag, const char *fmt, ...);

    /* Stalker (动态代码追踪), 语义见上方函数注释 */
    AlbStalkerHandle *(*stalker_new)(void);

    int (*stalker_free)(AlbStalkerHandle *stalker);

    AlbStalkerTransformerHandle *(*stalker_transformer_new)(
        AlbStalkerTransformFunc callback, void *user_data);

    void (*stalker_transformer_free)(AlbStalkerTransformerHandle *transformer);

    AlbStalkerEventSinkHandle *(*stalker_event_sink_new)(unsigned int event_mask,
                                                         AlbStalkerEventFunc callback, void *user_data);

    void (*stalker_event_sink_free)(AlbStalkerEventSinkHandle *sink);

    void (*stalker_follow_current_thread)(AlbStalkerHandle *stalker,
                                          AlbStalkerTransformerHandle *transformer,
                                          AlbStalkerEventSinkHandle *sink);

    void (*stalker_unfollow_current_thread)(AlbStalkerHandle *stalker);

    int (*stalker_follow)(AlbStalkerHandle *stalker, int thread_id,
                          AlbStalkerTransformerHandle *transformer,
                          AlbStalkerEventSinkHandle *sink);

    void (*stalker_unfollow)(AlbStalkerHandle *stalker, int thread_id);

    int (*stalker_follow_function)(AlbStalkerHandle *stalker, void *function,
                                   int instrument_flags, AlbStalkerTransformerHandle *transformer,
                                   AlbStalkerEventSinkHandle *sink);

    int (*stalker_unfollow_function)(AlbStalkerHandle *stalker, void *function);

    void (*stalker_exclude_range)(AlbStalkerHandle *stalker, void *base,
                                  unsigned long size);

    void (*stalker_exclude_module)(AlbStalkerHandle *stalker, const char *name);

    void (*stalker_include_module)(AlbStalkerHandle *stalker, const char *name);

    void (*stalker_exclude_named)(AlbStalkerHandle *stalker,
                                  const char *name_substr);

    void (*stalker_include_named)(AlbStalkerHandle *stalker,
                                  const char *name_substr);

    void (*stalker_clear_exclusions)(AlbStalkerHandle *stalker);

    void (*stalker_clear_inclusions)(AlbStalkerHandle *stalker);

    int (*stalker_iterator_next)(AlbStalkerIteratorHandle *iterator,
                                 AlbStalkerInstruction *instruction);

    void (*stalker_iterator_keep)(AlbStalkerIteratorHandle *iterator);

    void (*stalker_iterator_put_callout)(AlbStalkerIteratorHandle *iterator,
                                         AlbStalkerCalloutFunc callback, void *user_data);

    int (*stalker_context_get_register)(AlbStalkerContextHandle *context,
                                        const char *name, unsigned long *value);

    int (*stalker_context_set_register)(AlbStalkerContextHandle *context,
                                        const char *name, unsigned long value);

    unsigned long (*stalker_context_get_argument)(
        AlbStalkerContextHandle *context, int nth);

    int (*stalker_get_trust_threshold)(AlbStalkerHandle *stalker);

    void (*stalker_set_trust_threshold)(AlbStalkerHandle *stalker,
                                        int threshold);

    void (*stalker_invalidate)(AlbStalkerHandle *stalker, void *address);

    int (*stalker_garbage_collect)(AlbStalkerHandle *stalker);

    /* 库加载通知与延迟 hook, 语义见上方函数注释 */
    long (*register_library_callback)(const char *lib_filter,
                                      library_load_listener listener, void *user_data);

    int (*unregister_library_callback)(long id);

    long (*hook_func_delayed)(const char *lib_name, const char *symbol_name,
                              void *replace_func, delayed_hook_callback on_result, void *user_data);

    long (*hook_instrument_delayed)(const char *lib_name, const char *symbol_name,
                                    enter_listener on_enter, leave_listener on_leave, DetachCallback on_detach,
                                    delayed_hook_callback on_result, void *user_data, int instrument_flags);

    int (*get_delayed_hook_state)(long id);

    void *(*get_delayed_hook_address)(long id);

    void *(*get_delayed_hook_backup)(long id);

    int (*cancel_delayed_hook)(long id);

    /* 按偏移的延迟 hook, 语义见上方函数注释 */
    long (*hook_func_delayed_offset)(const char *lib_name, unsigned long offset,
                                     void *replace_func, delayed_hook_callback on_result, void *user_data);

    long (*hook_instrument_delayed_offset)(const char *lib_name, unsigned long offset,
                                           enter_listener on_enter, leave_listener on_leave, DetachCallback on_detach,
                                           delayed_hook_callback on_result, void *user_data, int instrument_flags);

    void *(*get_module_base)(void *handle);

    /* 无 Java VM 进程里创建 JVM(见 android_load_jvm 注释), 返回 JvmLoadResult */
    int (*android_load_jvm)(const char *libart_path, const char *classpath,
                            const char *library_path, int flags);

    /* 追踪窗口控制: 超时 / 事件数上限, 语义见上方函数注释(追加字段, 不改已有顺序) */
    int (*stalker_set_timeout)(AlbStalkerHandle *stalker, int timeout_ms);

    int (*stalker_set_max_events)(AlbStalkerHandle *stalker,
                                  unsigned long max_events);

    int (*stalker_get_stop_reason)(AlbStalkerHandle *stalker);

    unsigned long (*stalker_get_event_count)(AlbStalkerHandle *stalker);

    /* 目标是否走间接返回(sret)约定, 语义同 AlbatrossTargetUsesIndirectReturn(追加字段) */
    int (*target_uses_indirect_return)(void *address);

    /* 内置默认 native trace, 语义见上方 AlbStalkerTrace* 注释(追加字段, 不改已有顺序) */
    int (*stalker_trace_start)(const char *lib_name, const char *symbol_name,
                               void *address, int timeout_ms, unsigned long max_events,
                               unsigned int flags, const char *log_path);

    int (*stalker_trace_start_offset)(const char *lib_name, unsigned long offset,
                                      int timeout_ms, unsigned long max_events, unsigned int flags,
                                      const char *log_path);

    int (*stalker_trace_stop)(void);

    int (*stalker_trace_release)(void);

    int (*stalker_trace_state)(void);

    int (*stalker_trace_get_stop_reason)(void);

    int (*stalker_trace_status)(char *buffer, unsigned long size);

    int (*stalker_trace_dump)(char *buffer, unsigned long size);

    /* rwbp 内核读写断点一族 + journal（追加字段, 不改已有顺序; 语义见上方
     * AlbRwbpWatch* / AlbRwbpWatchJournal* 注释） */
    int (*rwbp_watch_supported)(void);

    int (*rwbp_watch_add)(int tid, void *address, unsigned long size, int type);

    int (*rwbp_watch_del)(int tid, void *address);

    int (*rwbp_watch_enable)(unsigned int ring_pages);

    int (*rwbp_watch_enable_v2)(const AlbRwbpWatchEnableOptions *options);

    int (*rwbp_watch_drain)(AlbRwbpWatchEvent *events, int max_events);

    int (*rwbp_watch_drain_v2)(AlbRwbpWatchEventV2 *events, int max_events);

    int (*rwbp_watch_status)(unsigned long *written, unsigned long *dropped, int *pid_filter);

    int (*rwbp_watch_status_v2)(AlbRwbpWatchStatsV2 *out);

    int (*rwbp_watch_stop)(void);

    int (*rwbp_watch_journal_start)(const AlbRwbpWatchJournalOptions *options);

    int (*rwbp_watch_journal_stop)(void);

    int (*rwbp_watch_journal_status)(AlbRwbpWatchJournalStats *out);

    /* PLT hook 一族（追加字段, 不改已有顺序; 语义见上方 AlbPlt* 注释） */
    AlbPltStub (*plt_hook_single)(const char *caller_path_name, const char *callee_path_name,
                                  const char *sym_name, void *new_func, alb_plt_hooked_callback hooked, void *hooked_arg);

    AlbPltStub (*plt_hook_all)(const char *callee_path_name, const char *sym_name, void *new_func,
                               alb_plt_hooked_callback hooked, void *hooked_arg);

    AlbPltStub (*plt_hook_partial)(alb_plt_caller_filter caller_filter, void *caller_filter_arg,
                                   const char *callee_path_name, const char *sym_name, void *new_func,
                                   alb_plt_hooked_callback hooked, void *hooked_arg);

    int (*plt_unhook)(AlbPltStub stub);

    int (*plt_add_ignore)(const char *caller_path_name);
} AlbatrossApi;

#define ALBATROSS_API_HAS_FIELD(api, field) \
  ((api) != 0 && (api)->struct_size >= \
      (unsigned int) (__builtin_offsetof(AlbatrossApi, field) + \
          sizeof(((AlbatrossApi *) 0)->field)))

#define ALBATROSS_API_HAS(api, field) ALBATROSS_API_HAS_FIELD(api, field)

/* 布局漂移防线:改了 AlbatrossApi 却没同步上面两个常数时,这里直接编译失败。
   (sizeof = 2 个 u32 + N 个指针,不含 padding;指针字段之间不会有填充。) */
#if defined(__cplusplus)
static_assert(sizeof(AlbatrossApi) ==
              sizeof(unsigned int) * 2 + ALBATROSS_API_TABLE_POINTER_FIELDS * sizeof(void *),
              "AlbatrossApi 布局变了:请同步更新 ALBATROSS_API_TABLE_POINTER_FIELDS 并 bump ALBATROSS_API_TABLE_VERSION");
#elif defined(__STDC_VERSION__) && __STDC_VERSION__ >= 201112L
_Static_assert(sizeof(AlbatrossApi) ==
        sizeof(unsigned int) * 2 + ALBATROSS_API_TABLE_POINTER_FIELDS * sizeof(void *),
    "AlbatrossApi 布局变了:请同步更新 ALBATROSS_API_TABLE_POINTER_FIELDS 并 bump ALBATROSS_API_TABLE_VERSION");
#endif

/* AlbatrossApiCheck 的返回码(可直接当 entry 的返回值;宿主机侧会看到
 * LOAD_LIB_INIT_ERR,并存进日志的具体原因)。 */
enum {
    ALB_API_OK = 0,
    ALB_API_ERR_NULL = 1,          /* api == NULL */
    ALB_API_ERR_VERSION = 2,       /* 宿主库的表版本低于 min_version */
    ALB_API_ERR_STRUCT_SIZE = 3,   /* 宿主库比本头文件旧:字段数不足,读尾部字段会崩 */
};

/* 插件 entry 开头的 ABI 自检:0 = 兼容可用,非 0 = 拒绝加载。
 *
 * 语义是"够不够"而不是"等不等":
 *   - 宿主库的表版本 < min_version          → ALB_API_ERR_VERSION
 *   - 宿主库 struct_size < 本头文件的 sizeof → ALB_API_ERR_STRUCT_SIZE(老库,必须拒绝)
 *   - 宿主库比本头文件新(之后又追加过字段)  → 通过(附录式扩展)
 *
 * 最常用写法(要求宿主库至少有本头文件的全部字段,最安全):
 *   int rc = AlbatrossApiCheck(api, ALBATROSS_API_TABLE_VERSION);
 *   if (rc) return rc;
 * 想兼容更老的库:min_version 传 1,并对超出老表范围的字段逐个 ALBATROSS_API_HAS_FIELD
 * 判断后再用(不要直接读尾部字段)。 */
#define ALB_API_INLINE static inline __attribute__((unused))
ALB_API_INLINE int AlbatrossApiCheck(const AlbatrossApi *api, unsigned int min_version) {
  if (api == 0)
    return ALB_API_ERR_NULL;
  if (api->abi_version < min_version)
    return ALB_API_ERR_VERSION;
  if (api->struct_size < (unsigned int) sizeof(AlbatrossApi))
    return ALB_API_ERR_STRUCT_SIZE;
  return ALB_API_OK;
}

typedef int (*AlbatrossLibEntry)(const AlbatrossApi *api, void *server);

#ifdef __cplusplus
};
#endif
#endif //ALBATROSS_HOOK_ALBATROSS_H
