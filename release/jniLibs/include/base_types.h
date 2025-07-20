//
// Created by QingWan on 24-5-10.
//

#ifndef ALBATROSS_HOOK_BASE_TYPES_H
#define ALBATROSS_HOOK_BASE_TYPES_H

typedef unsigned long long u64;
typedef unsigned int u32;
typedef unsigned short u16;
typedef unsigned char u8;
typedef long long i64;
typedef int i32;
typedef short i16;
typedef signed char i8;
typedef long isize;

#define size_to_u64(v) ((u64)(v))
#define size_to_u32(v) ((u32)(usize)(v))
#define size_to_i64(v) ((i64)(v))


#if defined(__i386__) || defined(__arm__)
#ifndef __WORDSIZE
#define __WORDSIZE 32
#endif
#define WORD_64 0
typedef __SIZE_TYPE__ usize;
#if defined(__arm__)
#undef size_to_u64
#undef size_to_i64
#define size_to_u64(v) (((u64)(v))&((1ull<<32)-1))
#define size_to_i64(v) (i64)size_to_u64(v)
#elif defined(__GNUC__)
#undef size_to_u64
#undef size_to_i64
#define size_to_u64(v) (((u64)(v))&((1ull<<32)-1))
#define size_to_i64(v) (i64)size_to_u64(v)
#endif
#else
#ifndef __WORDSIZE
#define __WORDSIZE 64
#endif
typedef unsigned long usize;
#define WORD_64 1
#endif
/**
 * pointing to the memory address that will be read and written to
 */
typedef u8 *ptr_t;
typedef const u8 *cptr_t;
/**
 * pointing to the executable code address in memory
 */
typedef u8 *fn_t;

typedef void *vptr_t;
/**
 * represents an address in memory,may not readable
 */
typedef usize addr_t;



#define C_INNER(name) _q_libc_##name

#ifdef USE_LIBC
#define C_FUNC(name) name

#else
#define C_FUNC C_INNER
#endif


extern usize C_INNER(page_size);

#define PAGE_SIZE C_INNER(page_size)
#define DEFAULT_PAGE_SIZE 4096
#define PAGE_MASK (~(PAGE_SIZE-1))
#define PAGE_START(x) ((x) & PAGE_MASK)
#define PAGE_END(x) PAGE_START(((usize)x + (PAGE_SIZE-1)))
#define PAGE_OFFSET(x) ((x) & (PAGE_SIZE-1))


#define I5_MASK  0x0000001fU
#define I6_MASK  0x0000003fU
#define I8_MASK  0x000000ffU
#define I10_MASK 0x000003ffU
#define I11_MASK 0x000007ffU
#define I12_MASK 0x00000fffU
#define I32_MAX  ((i32)  0x7fffffff)
#define I32_MIN  ((i32) (-I32_MAX - 1))
#define I64_MAX  ((i64)  0x7fffffffffffffffULL)
#define I64_MIN  ((i64) (-I64_MAX - 1))
#define U32_MAX  ((u32) 0xffffffff)
#define U8_MAX  ((u8) 0xff)
#define U16_MAX  (0xffff)
#define USIZE_MAX ((usize)(-1))
#define IS_WITHIN_INT32_RANGE(i) \
    (((i64) (i)) >= (i64) I32_MIN && \
     ((i64) (i)) <= (i64) I32_MAX)
#define IS_WITHIN_I8_RANGE(i) \
    (((i64) (i)) >=  (-128) && \
     ((i64) (i)) <=  (127))

#define IS_WITHIN_I11_RANGE(i) \
    (((i64) (i)) >=  (-1024L) && \
     ((i64) (i)) <=  (1023L))

#define IS_WITHIN_I20_RANGE(i) \
    (((i64) (i)) >=  (-524288L) && \
     ((i64) (i)) <=  (524287L))

#define IS_WITHIN_I26_RANGE(i) \
    (((i64) (i)) >=  (-33554432L) && \
     ((i64) (i)) <=  (33554431L))

#define IS_WITHIN_U7_RANGE(i) \
    (((i64) (i)) >=  (0L) && \
     ((i64) (i)) <=  (127L))

#define IS_WITHIN_U8_RANGE(i) \
    (((i64) (i)) >=  (0L) && \
     ((i64) (i)) <=  (255L))

#define U32_TO_LE(val)  ((u32) (val))
#define I32_TO_LE(val)  ((i32) (val))
#define U64_TO_LE(val)  ((u64) (val))
#define INT32_TO_LE(val)  ((i32) (val))
#define I64_TO_LE(val)  ((i64) (val))
#define U32_FROM_LE(val)  ((u32) (val))
#define U64_FROM_LE(val)  ((u64) (val))
#define I32_FROM_LE(val)  ((i32) (val))
#define I64_FROM_LE(val)  ((i64) (val))

#define PTR_TO_SIZE(p)  ((usize) (p))
#define SIZE_TO_PTR(v) ((void*)(v))

#define array_size(arr)    (sizeof (arr) / sizeof ((arr)[0]))
#define RWX_SUPPORT 1

#define ALIGN_DOWN(s, b) \
    ((((usize) (s))) & ~((usize) (b - 1)))
#define ALIGN(s, b) \
    ((((usize) (s)+(b-1))) & ~((usize) (b - 1)))
#undef  ABS
#define ABS(a)     (((a) < 0) ? -(a) : (a))

#ifndef MIN
#define MIN(x, y) ({ \
    typeof(x) _x = (x); \
    typeof(y) _y = (y); \
    (void) (&_x == &_y);        \
    _x < _y ? _x : _y; })

#define MAX(x, y) ({ \
    typeof(x) _x = (x); \
    typeof(y) _y = (y); \
    (void) (&_x == &_y);        \
    _x < _y ? _y : _x; })
#endif

#ifdef __cplusplus
#define newa(struct_type, n_structs) ((struct_type*) alloca (sizeof (struct_type) * (usize) (n_structs)))
#define BEGIN_C_FUNC  extern "C" {
#define END_C_FUNC    }
#else
#define BEGIN_C_FUNC
#define END_C_FUNC
#endif
#define alb_strip_code_pointer(n) n
#define alb_sign_code_pointer(n) n
#endif //ALBATROSS_HOOK_BASE_TYPES_H
