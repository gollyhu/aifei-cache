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
 * distribute under the License is distribute on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package cn.hg.aifei.cache.plugin;

import cn.aifei.aop.Aop;
import cn.aifei.aop.AopKit;
import cn.aifei.plugin.Plugin;
import cn.aifei.util.Prop;
import cn.aifei.util.StrUtil;
import cn.hg.aifei.cache.api.AbstractCache;
import cn.hg.aifei.cache.api.CacheException;
import cn.hg.aifei.cache.api.ICache;
import cn.hg.aifei.cache.api.ICacheProvider;
import cn.hg.aifei.cache.impl.local.CaffeineCacheProvider;
import cn.hg.aifei.cache.core.CacheManager;
import cn.hg.aifei.cache.impl.local.EhcacheCacheProvider;
import cn.hg.aifei.cache.impl.distribute.redis.JedisCacheProvider;
import cn.hg.aifei.cache.impl.distribute.redis.LettuceCacheProvider;
import cn.hg.aifei.cache.impl.local.LocalCacheProvider;
import cn.hg.aifei.cache.impl.distribute.redis.RedissonCacheProvider;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 缓存插件 - 实现 aifei Plugin 接口
 * <p>
 * 负责：
 * <ul>
 *   <li>初始化主缓存（必须配置）</li>
 *   <li>初始化扩展缓存（可选，通过 cache.names 声明）</li>
 *   <li>读取 namespace 配置并传递给缓存实例</li>
 *   <li>应用关闭时清理资源</li>
 * </ul>
 *
 * <p>配置示例（app-config.txt）：
 * <pre>
 * # ──────────────── 缓存配置 ────────────────
 * # 扩展缓存实例名称列表（逗号分隔）
 * cache.names = c1,local
 *
 * # 【主缓存，必须配置 - Jedis】
 * cache.type = jedis
 * cache.address = 127.0.0.1:6379
 * cache.password =
 * cache.database = 0
 * cache.ttl = 3600
 * # namespace 可空配置，不配置则默认 "aifei"
 * cache.namespace = aifei
 * # 序列化器，不配置则默认为 jdk 序列化器
 * cache.serializer = fastjson2
 *
 * # 【c1缓存 - Redisson】
 * cache.c1.type = redisson
 * cache.c1.address = 127.0.0.1:6379
 * cache.c1.ttl = 7200
 * # 自定义 namespace，不配置则默认 "aifei"
 * cache.c1.namespace = prefix
 *
 * # 【local缓存 - ConcurrentHashMap】
 * cache.local.type = local
 * cache.local.ttl = 0
 * cache.local.namespace = localPrefix
 * </pre>
 *
 * @author aifei
 */
public class CachePlugin implements Plugin {

    /** 主缓存名称（预留） */
    public static final String MAIN_CACHE_NAME = "main";

    /** 扩展缓存名称列表配置键 */
    protected static final String CACHE_NAMES_KEY = "cache.names";

    /** 主缓存类型配置键 */
    private static final String MAIN_TYPE_KEY = "cache.type";

    /** 主缓存配置前缀 */
    protected static final String MAIN_PREFIX = "cache.";

    /** 默认 namespace */
    private static final String DEFAULT_NAMESPACE = "aifei";

    /** Provider 配置键后缀（不含前导点，prefix 已含） */
    private static final String PROVIDER_SUFFIX = "provider";

    /** 扩展缓存类型配置键后缀（不含前导点，prefix 已含） */
    private static final String EXT_TYPE_SUFFIX = "type";

    /** namespace 配置键后缀 */
    private static final String NAMESPACE_SUFFIX = "namespace";

    /** nullValue 配置键后缀 */
    private static final String NULL_VALUE_SUFFIX = "nullValue";

    /** Provider 类型映射 */
    private static final Map<String, String> DEFAULT_PROVIDER_MAP = new HashMap<>();

    static {
        DEFAULT_PROVIDER_MAP.put("caffeine", CaffeineCacheProvider.class.getName());
        DEFAULT_PROVIDER_MAP.put("redisson", RedissonCacheProvider.class.getName());
        DEFAULT_PROVIDER_MAP.put("local", LocalCacheProvider.class.getName());
        DEFAULT_PROVIDER_MAP.put("jedis", JedisCacheProvider.class.getName());
        DEFAULT_PROVIDER_MAP.put("ehcache", EhcacheCacheProvider.class.getName());
        DEFAULT_PROVIDER_MAP.put("lettuce", LettuceCacheProvider.class.getName());
    }

    public static void addCacheProvider(String type, Class<? extends ICacheProvider> clazz) {
        DEFAULT_PROVIDER_MAP.put(type, clazz.getName());
    }

    /** 配置（子类可访问） */
    protected Prop prop;

    /** 缓存实例与 Provider 的映射（用于关闭时清理） */
    private final Map<ICache, ICacheProvider> cacheProviderMap = new ConcurrentHashMap<>();

    /** 已注册的缓存数量 */
    private int registeredCount = 0;

    /**
     * 构造函数
     *
     * @param prop 配置
     */
    public CachePlugin(Prop prop) {
        this.prop = prop;
    }

    @Override
    public void start() {

        // 1. 初始化主缓存（必配）
        initMainCache();

        // 2. 初始化扩展缓存（可选）
        initExtensionCaches();

        // 3. 注册注入式缓存
        registerInjectableCache();

    }

    /**
     * 读取指定配置前缀下的 namespace
     * <p>
     * 规则：
     * <ul>
     *   <li>读取 {@code <prefix>namespace} 配置项</li>
     *   <li>若未配置，默认返回 "aifei"</li>
     *   <li>若配置为空字符串，返回空字符串（不使用 namespace）</li>
     * </ul>
     *
     * @param prefix 配置前缀
     * @return namespace 值
     */
    private String resolveNamespace(String prefix) {
        String ns = prop.get(prefix + NAMESPACE_SUFFIX);
        if (ns == null) {
            // 未配置时默认使用 "aifei"
            return DEFAULT_NAMESPACE;
        }
        ns = ns.trim();
        if (ns.isEmpty()) {
            // 显式配置为空字符串，表示不使用 namespace
            return "";
        }
        return ns;
    }

    /**
     * 将 namespace 设置到 AbstractCache 实例上
     */
    private void setNamespaceOnCache(ICache cache, String namespace) {
        if (cache instanceof AbstractCache) {
            ((AbstractCache) cache).setNamespace(namespace);
        }
    }

    /**
     * 将 nullValue 设置到 AbstractCache 实例上
     */
    private void setNullValueOnCache(ICache cache, String prefix) {
        boolean nullValue = prop.getBoolean(prefix + NULL_VALUE_SUFFIX, false);
        if (cache instanceof AbstractCache) {
            ((AbstractCache) cache).setNullValue(nullValue);
        }
    }

    /**
     * 初始化主缓存
     * <p>
     * 主缓存必须配置 cache.type，否则启动报错
     */
    private void initMainCache() {
        String type = prop.get(MAIN_TYPE_KEY);
        if (StrUtil.isBlank(type)) {
            throw new CacheException("主缓存配置缺失：必须配置 'cache.type'，请检查配置文件。");
        }

        type = type.trim().toLowerCase();

        // 读取主缓存的 namespace
        String namespace = resolveNamespace(MAIN_PREFIX);

        // 创建 Provider
        ICacheProvider provider = createProvider(type, MAIN_PREFIX);
        if (provider == null) {
            throw new CacheException("主缓存初始化失败：无法创建 type=" + type + " 的 Provider");
        }

        // 构建缓存实例
        ICache cache = provider.buildCache(MAIN_CACHE_NAME, type, prop, MAIN_PREFIX);
        if (cache == null) {
            throw new CacheException("主缓存初始化失败：buildCache 返回 null");
        }

        // 设置 namespace
        setNamespaceOnCache(cache, namespace);

        // 设置 nullValue
        setNullValueOnCache(cache, MAIN_PREFIX);

        // 注册到 CacheManager
        CacheManager.registerCache(ICache.DEFAULT_CACHE_NAME, cache);
        cacheProviderMap.put(cache, provider);
        registeredCount++;

    }

    private static void registerInjectableCache() {
        try {
            AopKit.get().addSingletonObject(ICache.class, InjectableCache.INSTANCE);
        } catch (RuntimeException e) {
            ICache existing = currentAopCache();
            if (existing == InjectableCache.INSTANCE) {
                return;
            }
            throw new IllegalStateException("Aop singleton for ICache.class already exists", e);
        }
    }
    private static ICache currentAopCache() {
        try {
            return Aop.get(ICache.class);
        } catch (RuntimeException e) {
            return null;
        }
    }

    /**
     * 初始化扩展缓存
     * <p>
     * 通过 cache.names 配置项声明多个缓存实例
     */
    private void initExtensionCaches() {
        String namesStr = prop.get(CACHE_NAMES_KEY);
        if (StrUtil.isBlank(namesStr)) {
            return;
        }

        // 解析缓存名称
        String[] names = namesStr.split(",");
        for (String name : names) {
            name = name.trim();

            // 跳过空名称
            if (StrUtil.isBlank(name)) {
                continue;
            }

            // 跳过 "main"，已预留表示主缓存
            if (MAIN_CACHE_NAME.equalsIgnoreCase(name)) {
                continue;
            }

            initExtensionCache(name);
        }
    }

    /**
     * 初始化单个扩展缓存
     *
     * @param name 缓存名称
     */
    private void initExtensionCache(String name) {
        String prefix = MAIN_PREFIX + name + ".";
        String type = prop.get(prefix + EXT_TYPE_SUFFIX);

        if (StrUtil.isBlank(type)) {
            return;
        }

        type = type.trim().toLowerCase();

        // 读取扩展缓存的 namespace
        String namespace = resolveNamespace(prefix);

        // 创建 Provider
        ICacheProvider provider = createProvider(type, prefix);
        if (provider == null) {
            return;
        }

        // 构建缓存实例
        ICache cache = provider.buildCache(name, type, prop, prefix);
        if (cache == null) {
            return;
        }

        // 设置 namespace
        setNamespaceOnCache(cache, namespace);

        // 设置 nullValue
        setNullValueOnCache(cache, prefix);

        // 注册到 CacheManager
        CacheManager.registerCache(name, cache);
        cacheProviderMap.put(cache, provider);
        registeredCount++;

    }

    /**
     * 根据类型创建 Provider
     *
     * @param type 缓存类型
     * @param prefix 配置前缀（用于日志）
     * @return Provider 实例
     */
    private ICacheProvider createProvider(String type, String prefix) {
        // 尝试使用配置的 Provider 类
        String providerClass = prop.get(prefix + PROVIDER_SUFFIX);
        if (StrUtil.isBlank(providerClass)) {
            // 使用默认 Provider
            providerClass = DEFAULT_PROVIDER_MAP.get(type);
            if (StrUtil.isBlank(providerClass)) {
                return null;
            }
        }

        return instantiateProvider(providerClass);
    }

    /**
     * 通过反射实例化 Provider
     *
     * @param providerClass Provider 全类名
     * @return Provider 实例
     */
    private ICacheProvider instantiateProvider(String providerClass) {
        try {
            Class<?> clazz = Class.forName(providerClass);
            if (!ICacheProvider.class.isAssignableFrom(clazz)) {
                return null;
            }
            return (ICacheProvider) clazz.newInstance();
        } catch (ClassNotFoundException | IllegalAccessException | InstantiationException e) {
            return null;
        }
    }

    @Override
    public void stop() {

        // 清空所有缓存
        CacheManager.clearAll();

        // 通过 ICacheProvider.shutdown() 统一关闭所有 Provider
        for (ICacheProvider provider : cacheProviderMap.values()) {
            try {
                provider.shutdown();
            } catch (Exception e) {
                // 关闭 Provider 失败时静默忽略
            }
        }

        cacheProviderMap.clear();
        registeredCount = 0;

    }

    /**
     * 添加外部缓存实例（不通过配置文件）
     * <p>
     * 可用于动态添加缓存或在 start() 之前预先注册缓存。
     *
     * @param name  缓存名称
     * @param cache 缓存实例
     */
    public static void addCache(String name, ICache cache) {
        CacheManager.registerCache(name, cache);
    }

    /**
     * 获取缓存实例
     *
     * @param name 缓存名称
     * @return 缓存实例
     */
    public static ICache getCache(String name) {
        return CacheManager.getCache(name);
    }

    /**
     * 移除缓存实例
     *
     * @param name 缓存名称
     * @return 被移除的缓存
     */
    public static ICache removeCache(String name) {
        return CacheManager.removeCache(name);
    }
}
