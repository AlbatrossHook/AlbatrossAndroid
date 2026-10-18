/*
 * Copyright 2025 QingWan (qingwanmail@foxmail.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package qing.albatross.nativehook;

import java.util.Map;

/**
 * 进程内 tcc 动态编译的 C 模块(对齐 frida CModule)。
 *
 * <pre>{@code
 * CModule cm = CModule.compile(
 *     "#include <albatross.h>\n"
 *     + "static int count = 0;\n"
 *     + "void on_enter(InvocationContext *ctx, void *data) { count++; }\n"
 *     + "int alb_count(void) { return count; }\n");
 * NativeFunction f = cm.getFunction("alb_count", Const.ARG_INT);
 * int count = (Integer) f.call();
 * ...
 * cm.close();
 * }</pre>
 *
 * <p>可用头文件(虚拟提供, -nostdinc): {@code albatross.h}(全部框架 API)、
 * {@code cmodule.h}(便捷聚合)、{@code string.h/stdlib.h/stdio.h/math.h/
 * errno.h/stdint.h/inttypes.h/stddef.h/stdarg.h/stdbool.h/float.h/
 * stdatomic.h}、{@code android/log.h}、{@code sys/mman.h}、{@code unistd.h}、
 * {@code pthread.h}。
 *
 * <p>C 代码可直接调用 {@code albatross.h} 的全部函数与常见 libc 函数;
 * 也可在 {@link #compile(String, Map)} 传入外部符号(name→地址),
 * C 源码以 {@code extern} 声明直接引用。
 *
 * <p><b>生命周期</b>: 相同 (源码, 选项, 注入符号) 的多次 {@code compile} 共享
 * 底层模块(编译缓存, 引用计数), 各实例独立 {@code close()}。
 * 源码可定义两个可选回调(对齐 frida CModule 的 init/finalize, 都是"按模块一次",
 * 缓存命中不重跑 init):
 * <pre>{@code
 * int  alb_module_init(void);  // 加载回调: 编译成功后、compile() 返回前调用;
 *                              // 返回非 0 判加载失败(compile 抛异常, 模块销毁)
 * void alb_module_fini(void);  // 卸载回调: 最后一个引用 close、代码页释放前调用
 * }</pre>
 * C 代码内通过 AlbatrossHookInstrument 等安装的 hook, 可以在 {@code alb_module_fini}
 * 里卸载(否则必须在最后一个引用 {@link #close()} 前自行卸载, 代码页释放后回调悬空)。
 * 回调在编译锁内执行: 里面不能再 compile/close 其它 CModule(非递归锁, 会死锁)。
 */
public final class CModule implements AutoCloseable {

  private long handle;

  private CModule(long handle) {
    this.handle = handle;
  }

  /**
   * 编译 C 源码, 失败抛 {@link CModuleCompileException}。
   */
  public static CModule compile(String source) {
    return compile(source, null, null);
  }

  /**
   * 编译并注入外部符号: symbols 的 name→address 在 C 源码里以
   * {@code extern} 声明引用(函数或变量皆可)。
   */
  public static CModule compile(String source, Map<String, Long> symbols) {
    return compile(source, symbols, null);
  }

  /**
   * @param options 附加 tcc 选项, 如 "-DFOO=1"; null 为默认
   */
  public static CModule compile(String source, Map<String, Long> symbols, String options) {
    String[] names = null;
    long[] addrs = null;
    if (symbols != null && !symbols.isEmpty()) {
      int count = 0;
      for (String key : symbols.keySet()) {
        if (key != null)
          count++;
      }
      if (count > 0) {
        names = new String[count];
        addrs = new long[count];
        int i = 0;
        for (Map.Entry<String, Long> entry : symbols.entrySet()) {
          if (entry.getKey() == null)
            continue; // HashMap 允许 null key, native 不接受
          names[i] = entry.getKey();
          Long v = entry.getValue();
          addrs[i] = v == null ? 0 : v;
          i++;
        }
      }
    }
    String[] error = new String[1];
    long handle = AlbNative.cmoduleCompileNative(source, options, names, addrs, error);
    if (handle == 0)
      throw new CModuleCompileException(error[0] == null ? "compile failed" : error[0]);
    return new CModule(handle);
  }

  /**
   * 取 C 源码内定义的符号(函数或全局变量)地址, 不存在返回 0。
   */
  public synchronized long getAddress(String symbol) {
    if (handle == 0)
      throw new IllegalStateException("CModule already closed");
    if (symbol == null)
      return 0;
    return AlbNative.cmoduleGetSymbolNative(handle, symbol);
  }

  public boolean hasSymbol(String symbol) {
    return getAddress(symbol) != 0;
  }

  /**
   * 取符号并包装为可调用函数, 符号不存在抛 IllegalArgumentException。
   */
  public NativeFunction getFunction(String symbol, byte retType, byte... argTypes) {
    long address = getAddress(symbol);
    if (address == 0)
      throw new IllegalArgumentException("CModule symbol not found: " + symbol);
    return new NativeFunction(address, retType, argTypes);
  }

  /**
   * 释放一次模块引用; 相同 (源码, 选项, 注入符号) 的编译会共享底层模块,
   * 每个引用各自 close, 最后一个引用才真正回收代码内存。
   * 调用前必须卸载 C 侧安装的 hook。
   */
  @Override
  public synchronized void close() {
    if (handle != 0) {
      AlbNative.cmoduleFreeNative(handle);
      handle = 0;
    }
  }

  /**
   * 忘记 close 时的 best-effort 兜底(ART 上 finalize 仍生效);
   * 确定性释放请显式 close()/try-with-resources——finalize 时机不可控,
   * 且此时 C 侧若仍有 hook 会直接崩溃。
   */
  @SuppressWarnings("deprecation")
  @Override
  protected void finalize() throws Throwable {
    try {
      close();
    } finally {
      super.finalize();
    }
  }
}
