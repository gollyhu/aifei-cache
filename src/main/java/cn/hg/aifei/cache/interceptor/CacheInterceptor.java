/*
 * Copyright 2021-2035 gollyhu (https://github.com/gollyhu)
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

import cn.aifei.aop.Interceptor;
import cn.aifei.aop.Invocation;
import cn.aifei.core.Input;
import cn.aifei.util.StrUtil;
import cn.hg.aifei.cache.annotation.CacheEvict;
import cn.hg.aifei.cache.annotation.CachePut;
import cn.hg.aifei.cache.annotation.CachesEvict;
import cn.hg.aifei.cache.api.ICache;
import cn.hg.aifei.cache.core.CacheManager;

import java.lang.reflect.Method;

/**
 * 缓存拦截器 - 基于 aifei AOP 框架实现对 Service 方法的自动缓存。
 *
 * <h3>工作流程</h3>
 * <ol>
 *   <li>解析方法上的缓存注解（@CachePut / @CacheEvict / @CachesEvict）</li>
 *   <li>构建缓存 Key：调用 KeyGenerator 动态计算真实 Key</li>
 *   <li>拦截目标方法执行，按注解语义处理缓存读写</li>
 * </ol>
 *
 * <h3>使用方式</h3>
 * 通过 aifei 的 @Before 注解应用到 Service 方法或类上：
 * <pre>
 *   &#64;Before(CacheInterceptor.class)
 *   &#64;CachePut(name = "product", key = "prod-#p(id)", ttlSeconds = 300)
 *   public Product getProduct(Long id) { ... }
 * </pre>
 * 或注册为全局拦截器。
 *
 * @author aifei
 */
public class CacheInterceptor implements Interceptor {

    @Override
    public void intercept(Invocation inv) throws Throwable {
        Method method = inv.getMethod();
        Input input = inv.getInput();

        // 1. 获取注解
        CachePut cachePut = method.getAnnotation(CachePut.class);
        CacheEvict cacheEvict = method.getAnnotation(CacheEvict.class);
        CachesEvict cachesEvict = method.getAnnotation(CachesEvict.class);

        // 2. 缓存优先：@CachePut — 命中返回，未命中执行后写入
        if (cachePut != null) {
            handleCacheFirst(inv, cachePut.name(), cachePut.key(),
                    cachePut.ttlSeconds(), cachePut.nullable(), cachePut.cache(), input,
                    cacheEvict, cachesEvict);
            return;
        }

        try {
            // 3. 先执行目标方法
            inv.invoke();
        } finally {
            // 4. 处理 @CacheEvict（方法执行后清除缓存）
            if (cacheEvict != null) {
                handleCacheEvict(cacheEvict, inv.getMethod(), getMethodArgs(inv), input);
            }

            // 5. 处理 @CachesEvict（批量清除）
            if (cachesEvict != null) {
                for (CacheEvict evict : cachesEvict.value()) {
                    handleCacheEvict(evict, inv.getMethod(), getMethodArgs(inv), input);
                }
            }
        }
    }

    /**
     * 缓存优先逻辑：先查缓存，命中直接返回；未命中则执行方法并写入缓存。
     */
    private void handleCacheFirst(Invocation inv, String name, String keyExpr,
                                   int ttlSeconds, boolean nullable, String cacheName, Input input,
                                  CacheEvict cacheEvict, CachesEvict cachesEvict) throws Throwable {
        ICache cache = resolveCache(cacheName);
        String key = KeyGenerator.generate(keyExpr, inv.getMethod(), getMethodArgs(inv), input);

        // 1. 尝试从缓存获取
        Object cachedValue = cache.get(name, key);
        if (cachedValue != null) {
            inv.setReturnValue(cachedValue);
            return;
        }

        try {
            // 2. 缓存未命中，执行目标方法
            inv.invoke();
            Object result = inv.getReturnValue();

            // 写入缓存
            if (result != null || nullable) {
                long ttl = ttlSeconds > 0 ? ttlSeconds : cache.getDefaultTtl();
                cache.put(name, key, result, ttl);
            }
        } finally {
            // 3. 处理 @CacheEvict（方法执行后清除缓存）
            if (cacheEvict != null) {
                handleCacheEvict(cacheEvict, inv.getMethod(), getMethodArgs(inv), input);
            }

            // 4. 处理 @CachesEvict（批量清除）
            if (cachesEvict != null) {
                for (CacheEvict evict : cachesEvict.value()) {
                    handleCacheEvict(evict, inv.getMethod(), getMethodArgs(inv), input);
                }
            }
        }
    }

    /**
     * 处理 @CacheEvict 注解。
     * 在目标方法执行后调用，删除指定 Key 或清空整个缓存分组。
     */
    private void handleCacheEvict(CacheEvict annotation, Method method, Object[] args, Input input) {
        ICache cache = resolveCache(annotation.cache());
        String[] rawKeys = annotation.key();

        if (rawKeys.length == 0) {
            cache.clear(annotation.name());
            return;
        }

        for (String keyExpr : rawKeys) {
            try {
                String key = KeyGenerator.generate(keyExpr, method, args, input);
                cache.evict(annotation.name(), key);
            } catch (Exception e) {
                // evict 失败时静默忽略
            }
        }
    }

    /**
     * 解析缓存实例名称，若为空则使用默认缓存实例。
     */
    private ICache resolveCache(String cacheName) {
        if (StrUtil.isBlank(cacheName)) {
            cacheName = ICache.DEFAULT_CACHE_NAME;
        }
        ICache cache = CacheManager.getCache(cacheName);
        if (cache == null) {
            cache = CacheManager.getCache(ICache.DEFAULT_CACHE_NAME);
        }
        return cache;
    }

    /**
     * 从 Invocation 获取方法参数值。
     * <p>
     * 对于 AOP 代理调用场景：{@code inv.getArgs()} 在 invoke() 前已经包含实参，直接返回。
     * 对于 HTTP Action 直接拦截场景：getArgs() 在 invoke() 前为 null，此时通过参数名
     * 从 Input 中合成简单类型值作为回退方案。
     * </p>
     */
    private Object[] getMethodArgs(Invocation inv) {
        Object[] args = inv.getArgs();
        if (args != null) {
            return args;
        }

        // 回退：从 Input 获取参数值（HTTP Action 直接拦截时）
        Input input = inv.getInput();
        Method method = inv.getMethod();
        if (input == null) {
            return new Object[0];
        }

        java.lang.reflect.Parameter[] params = method.getParameters();
        Object[] syntheticArgs = new Object[params.length];
        for (int i = 0; i < params.length; i++) {
            syntheticArgs[i] = input.getStr(params[i].getName());
        }
        return syntheticArgs;
    }
}
