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
 * distribute under the License is distribute on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package cn.hg.aifei.cache.core;

import cn.hg.aifei.cache.api.ICache;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 缓存管理器 - 单例模式，线程安全
 * <p>
 * 管理所有 ICache 实例，提供静态方法访问。
 * 配合 CachePlugin 使用，在应用启动时注册缓存实例。
 *
 * <pre>
 * 使用示例：
 *   // 在 Service 中获取缓存
 *   ICache cache = CacheManager.getCache("main");
 *   cache.put("myGroup", "key", "value");
 *
 *   // 移除缓存
 *   CacheManager.removeCache("main");
 * </pre>
 *
 * @author aifei
 */
public class CacheManager {

    /**
     * 单例持有类 - 延迟加载，线程安全
     */
    private static class Holder {
        private static final CacheManager INSTANCE = new CacheManager();
    }

    /**
     * 缓存实例存储 - 线程安全
     */
    private final Map<String, ICache> cacheMap = new ConcurrentHashMap<>();

    /**
     * 私有构造函数，防止外部实例化
     */
    private CacheManager() {
        cacheMap.put(ICache.DEFAULT_CACHE_NAME, new ICache() {
            @Override
            public void put(String cacheName, String key, Object value, long ttlSeconds) {}

            @Override
            public <T> T get(String cacheName, String key) {return null;}

            @Override
            public void evict(String cacheName, String... keys) {}

            @Override
            public void clearAll() {}

            @Override
            public String getName() {return ICache.DEFAULT_CACHE_NAME;}

            @Override
            public String getType() {return "dummy";}

            @Override
            public <T> T getNativeCache() {return (T) this;}

            @Override
            public long getDefaultTtl() {return 0;}

            @Override
            public void setDefaultTtl(long ttlSeconds) {}

            @Override
            public Set<String> getCacheNames() {return Collections.emptySet();}

            @Override
            public void clear(String... cacheNames) {}
        });
    }

    /**
     * 获取 CacheManager 单例实例
     */
    public static CacheManager getInstance() {
        return Holder.INSTANCE;
    }

    /**
     * 注册缓存实例
     *
     * @param name  缓存名称（唯一标识）
     * @param cache 缓存实例
     */
    public static void registerCache(String name, ICache cache) {
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("Cache name can not be null or empty");
        }
        if (cache == null) {
            throw new IllegalArgumentException("Cache instance can not be null");
        }
        getInstance().cacheMap.put(name, cache);
    }

    /**
     * 根据名称获取缓存实例
     *
     * @param name 缓存名称
     * @return 缓存实例，如果不存在返回 null
     */
    public static ICache getCache(String name) {
        if (name == null) {
            return null;
        }
        return getInstance().cacheMap.get(name);
    }

    /**
     * 移除并销毁指定缓存实例
     * <p>
     * 会调用 cache.clearAll() 清理数据
     *
     * @param name 缓存名称
     * @return 被移除的缓存实例（如果存在）
     */
    public static ICache removeCache(String name) {
        ICache cache = getInstance().cacheMap.remove(name);
        if (cache != null) {
            cache.clearAll();
        }
        return cache;
    }

    /**
     * 获取所有已注册的缓存名称
     */
    public static Map<String, ICache> getAllCaches() {
        return new ConcurrentHashMap<>(getInstance().cacheMap);
    }

    /**
     * 移除所有缓存实例
     * <p>
     * 通常在应用关闭时调用
     */
    public static void clearAll() {
        getInstance().cacheMap.clear();
    }

    /**
     * 检查缓存是否存在
     */
    public static boolean containsCache(String name) {
        return getInstance().cacheMap.containsKey(name);
    }

    /**
     * 获取已注册缓存数量
     */
    public static int size() {
        return getInstance().cacheMap.size();
    }
}
