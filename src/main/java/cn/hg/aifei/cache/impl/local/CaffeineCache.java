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

package cn.hg.aifei.cache.impl.local;

import cn.hg.aifei.cache.api.AbstractCache;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import java.io.Serializable;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Caffeine 缓存实现
 * <p>
 * 基于 Caffeine 的高性能本地缓存，支持容量限制和 TTL。
 * 使用 expireAfterWrite 策略，确保写入后自动过期。
 *
 * @author aifei
 */
public class CaffeineCache extends AbstractCache {

    private static final String TYPE = "caffeine";

    /**
     * 原生 Caffeine Cache 实例
     */
    private final Cache<String, Object> cache;

    /**
     * 已记录的 cacheName 集合（本地内存，线程安全）
     */
    private final Set<String> cacheNames = ConcurrentHashMap.newKeySet();

    /**
     * 构造函数
     *
     * @param name        缓存名称
     * @param maxSize     最大容量
     * @param defaultTtl  默认 TTL 秒数
     */
    public CaffeineCache(String name, int maxSize, long defaultTtl) {
        super(name, defaultTtl);
        this.cache = Caffeine.newBuilder()
                .maximumSize(maxSize)
                .expireAfterWrite(defaultTtl, java.util.concurrent.TimeUnit.SECONDS)
                .recordStats()
                .build();
    }

    @Override
    public String getType() {
        return TYPE;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T getNativeCache() {
        return (T) cache;
    }

    // ==================== cacheName 元数据 ====================

    @Override
    protected void onPutCacheName(String cacheName) {
        if (cacheName != null && !cacheName.isEmpty()) {
            cacheNames.add(cacheName);
        }
    }

    @Override
    protected void doClearCacheNamesMeta() {
        cacheNames.clear();
    }

    @Override
    public Set<String> getCacheNames() {
        return Collections.unmodifiableSet(cacheNames);
    }

    @Override
    public void clear(String... names) {
        if (names == null) {
            return;
        }
        for (String cn : names) {
            if (cn == null || cn.trim().isEmpty()) {
                continue;
            }
            String prefix = buildKeyPrefix(cn);
            if (prefix.isEmpty()) {
                // 无 prefix 意味着清空所有数据
                doClear();
                cacheNames.clear();
                return;
            }
            // 遍历所有 key，清除匹配前缀的条目
            for (String key : cache.asMap().keySet()) {
                if (key.startsWith(prefix)) {
                    cache.invalidate(key);
                }
            }
            cacheNames.remove(cn);
        }
    }

    // ==================== 内部实现 ====================

    @Override
    protected void doPut(String key, Object value, long ttlSeconds) {
        CaffeineCacheObject cco = new CaffeineCacheObject(value, ttlSeconds);
        cco.setCachetime(System.currentTimeMillis());
        cache.put(key, cco);
    }

    @Override
    @SuppressWarnings("unchecked")
    protected <T> T doGet(String key) {
        CaffeineCacheObject data = (CaffeineCacheObject) cache.getIfPresent(key);
        if (data == null) {
            return null;
        }

        if (data.isDue()) {
            cache.invalidate(key);
            return null;
        }

        return (T) data.getValue();
    }

    @Override
    protected void doEvict(String key) {
        cache.invalidate(key);
    }

    @Override
    protected void doClear() {
        cache.invalidateAll();
    }

    // ==================== 统计信息 ====================

    /**
     * 获取缓存统计信息
     */
    public CacheStats getStats() {
        return new CacheStats(
                cache.stats().hitCount(),
                cache.stats().missCount(),
                cache.stats().hitRate(),
                cache.stats().evictionCount(),
                cache.stats().evictionWeight()
        );
    }

    /**
     * 缓存统计信息
     */
    public static class CacheStats {
        private final long hitCount;
        private final long missCount;
        private final double hitRate;
        private final long evictionCount;
        private final long evictionWeight;

        public CacheStats(long hitCount, long missCount, double hitRate,
                          long evictionCount, long evictionWeight) {
            this.hitCount = hitCount;
            this.missCount = missCount;
            this.hitRate = hitRate;
            this.evictionCount = evictionCount;
            this.evictionWeight = evictionWeight;
        }

        public long getHitCount() { return hitCount; }
        public long getMissCount() { return missCount; }
        public double getHitRate() { return hitRate; }
        public long getEvictionCount() { return evictionCount; }
        public long getEvictionWeight() { return evictionWeight; }
    }

    // ==================== 缓存包装对象 ====================

    /**
     * Caffeine 缓存值包装对象，携带 per-entry TTL 信息
     * <p>
     * 由于 Caffeine 的 expireAfterWrite 对所有条目使用相同策略，
     * 通过包装对象实现每个 key 独立设置 TTL。<br>
     * isDue() 方法用于惰性过期检查，getTtl() 返回剩余秒数。
     */
    protected static class CaffeineCacheObject implements Serializable {
        private Object value;
        private long liveSeconds;
        private long cachetime;

        public CaffeineCacheObject(Object value) {
            this.value = value;
        }

        public CaffeineCacheObject(Object value, long liveSeconds) {
            this.value = value;
            this.liveSeconds = liveSeconds;
        }

        public Object getValue() {
            return value;
        }

        public void setValue(Object value) {
            this.value = value;
        }

        public long getTtl() {
            long timeMillis = cachetime - System.currentTimeMillis() - liveSeconds * 1000;

            if (timeMillis > 0) {
                return timeMillis / 1000;
            }

            return 0;
        }

        public long getCachetime() {
            return cachetime;
        }

        public void setCachetime(long cachetime) {
            this.cachetime = cachetime;
        }

        public boolean isDue() {
            return System.currentTimeMillis() - liveSeconds * 1000 > cachetime;
        }
    }
}
