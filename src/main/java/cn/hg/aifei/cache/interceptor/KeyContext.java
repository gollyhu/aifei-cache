/*
 * Copyright 2021-2035 糊搞 (https://github.com/gollyhu)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package cn.hg.aifei.cache.interceptor;

import cn.aifei.core.Input;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Key 生成线程上下文，通过 ThreadLocal 在 Enjoy 指令中传递运行时数据。
 */
public class KeyContext {

    private static final ThreadLocal<KeyContext> HOLDER = new ThreadLocal<>();

    public final Method method;
    public final Object[] args;
    public final Input input;
    public final Map<String, Integer> paramIndex;

    KeyContext(Method method, Object[] args, Input input, Map<String, Integer> paramIndex) {
        this.method = method;
        this.args = args;
        this.input = input;
        this.paramIndex = paramIndex;
    }

    public static void set(KeyContext ctx) {
        HOLDER.set(ctx);
    }

    public static KeyContext get() {
        return HOLDER.get();
    }

    public static void remove() {
        HOLDER.remove();
    }

    /** 缓存方法参数名 → 索引映射 */
    private static final Map<Method, Map<String, Integer>> INDEX_CACHE = new ConcurrentHashMap<>();

    public static Map<String, Integer> buildParamIndex(Method method) {
        return INDEX_CACHE.computeIfAbsent(method, m -> {
            Map<String, Integer> map = new ConcurrentHashMap<>();
            java.lang.reflect.Parameter[] params = m.getParameters();
            for (int i = 0; i < params.length; i++) {
                String name = params[i].getName();
                if (name != null) {
                    map.put(name, i);
                }
                map.put("arg" + i, i);
            }
            return map;
        });
    }

    /** 按参数名获取值 */
    public Object getArgByName(String name) {
        Integer idx = paramIndex.get(name);
        return idx != null && idx < args.length ? args[idx] : null;
    }
}
