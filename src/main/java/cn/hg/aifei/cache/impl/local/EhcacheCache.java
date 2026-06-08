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
import org.ehcache.Cache;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Ehcache 缓存实现
 * <p>
 * 基于 Ehcache 3.x 的本地缓存，支持：
 * <ul>
 *   <li>堆内 / 堆外 / 磁盘多级存储</li>
 *   <li>丰富的过期策略</li>
 *   <li>JSR-107 (JCache) 兼容</li>
 * </ul>
 *
 * <p>实现说明：
 * <ul>
 *   <li>put(key, value, ttl) 使用包装对象实现 per-entry TTL</li>
 *   <li>setTtl / getTtl 基于包装对象实现</li>
 * </ul>
 *
 * @author aifei
 */
public class EhcacheCache extends AbstractCache {

    private static final String TYPE = "ehcache";

    /**
     * Ehcache Cache 实例
     */
    private final Cache<String, Object> cache;

    /**
     * 已记录的 cacheName 集合（本地内存，线程安全）
     */
    private final Set<String> cacheNames = ConcurrentHashMap.newKeySet();

    /**
     * 构造函数
     *
     * @param cache      Ehcache Cache 实例
     * @param name       缓存名称
     * @param defaultTtl 默认 TTL 秒数
     */
    public EhcacheCache(Cache<String, Object> cache, String name, long defaultTtl) {
        super(name, defaultTtl);
        this.cache = cache;
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
                doClear();
                cacheNames.clear();
                return;
            }
            // 遍历 Ehcache 条目，清除匹配前缀的 key
            for (Cache.Entry<String, Object> entry : cache) {
                if (entry.getKey().startsWith(prefix)) {
                    cache.remove(entry.getKey());
                }
            }
            cacheNames.remove(cn);
        }
    }

    // ==================== 内部实现 ====================

    @Override
    protected void doPut(String key, Object value, long ttlSeconds) {
        if (ttlSeconds > 0) {
            cache.put(key, new TtlEntry(value, System.currentTimeMillis() + ttlSeconds * 1000));
        } else {
            cache.put(key, new TtlEntry(value, 0));
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    protected <T> T doGet(String key) {
        Object entry = cache.get(key);
        if (entry == null) {
            return null;
        }
        if (entry instanceof TtlEntry) {
            TtlEntry ttlEntry = (TtlEntry) entry;
            if (ttlEntry.isExpired()) {
                cache.remove(key);
                return null;
            }
            return (T) ttlEntry.getValue();
        }
        return (T) entry;
    }

    @Override
    protected void doEvict(String key) {
        cache.remove(key);
    }

    @Override
    protected void doClear() {
        cache.clear();
    }

    // ==================== TTL 操作 ====================

    @Override
    public boolean setTtl(String cacheName, String key, long ttlSeconds) {
        if (key == null) {
            return false;
        }
        String storageKey = buildStorageKey(cacheName, key);
        Object entry = cache.get(storageKey);
        if (entry == null) {
            return false;
        }
        if (entry instanceof TtlEntry) {
            TtlEntry ttlEntry = (TtlEntry) entry;
            long newExpireTime = ttlSeconds > 0
                    ? System.currentTimeMillis() + ttlSeconds * 1000
                    : 0;
            cache.put(storageKey, new TtlEntry(ttlEntry.getValue(), newExpireTime));
            return true;
        }
        // 非 TtlEntry 包装的值，重新包装
        long newExpireTime = ttlSeconds > 0
                ? System.currentTimeMillis() + ttlSeconds * 1000
                : 0;
        cache.put(storageKey, new TtlEntry(entry, newExpireTime));
        return true;
    }

    @Override
    public long getTtl(String cacheName, String key) {
        if (key == null) {
            return -2;
        }
        String storageKey = buildStorageKey(cacheName, key);
        Object entry = cache.get(storageKey);
        if (entry == null) {
            return -2;
        }
        if (entry instanceof TtlEntry) {
            TtlEntry ttlEntry = (TtlEntry) entry;
            if (ttlEntry.getExpireTime() == 0) {
                return -1; // 永不过期
            }
            long remaining = ttlEntry.getExpireTime() - System.currentTimeMillis();
            return remaining > 0 ? remaining / 1000 : -2;
        }
        return -1; // 非包装对象，永不过期
    }

    // ==================== 统计 ====================

    /**
     * 获取当前缓存条目数（Ehcache 3.x 无直接 size 方法，通过迭代器估算）
     */
    public long size() {
        long count = 0;
        for (Cache.Entry<String, Object> entry : cache) {
            count++;
        }
        return count;
    }

    // ==================== TTL 包装对象 ====================

    /**
     * 带 TTL 的缓存条目
     */
    protected static class TtlEntry {
        private final Object value;
        private final long expireTime; // 过期时间戳（毫秒），0 表示永不过期

        public TtlEntry(Object value, long expireTime) {
            this.value = value;
            this.expireTime = expireTime;
        }

        public Object getValue() {
            return value;
        }

        public long getExpireTime() {
            return expireTime;
        }

        public boolean isExpired() {
            return expireTime > 0 && System.currentTimeMillis() > expireTime;
        }
    }
}
