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


#ifndef MIN
#define MIN(x, y) ({ \
    typeof(x) _x = (x); \
    typeof(y) _y = (y); \
    (void) (&_x == &_y);        \
    _x < _y ? _x : _y; })

#endif

#ifdef __cplusplus
#define newa(struct_type, n_structs) ((struct_type*) alloca (sizeof (struct_type) * (usize) (n_structs)))
#define BEGIN_C_FUNC  extern "C" {
#define END_C_FUNC    }
#else
#define BEGIN_C_FUNC
#define END_C_FUNC
#endif
#endif //ALBATROSS_HOOK_BASE_TYPES_H
