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

package cn.hg.aifei.cache.impl.distribute.redis;

import cn.hg.aifei.cache.impl.distribute.AbstractDistributedCache;
import org.redisson.api.RMapCache;
import org.redisson.api.RSet;
import org.redisson.api.RedissonClient;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Redisson 缓存实现
 * <p>
 * 基于 Redisson RMapCache 的分布式缓存，支持：
 * <ul>
 *   <li>集群/哨兵/主从模式</li>
 *   <li>每个 key 独立过期时间</li>
 *   <li>分布式原子操作</li>
 * </ul>
 *
 * <p>缓存分组元数据使用 Redis Set 存储（key = namespace:cacheNames 或 cacheNames）。
 *
 * @author aifei
 */
public class RedissonCache extends AbstractDistributedCache {

    private static final String TYPE = "redisson";

    /**
     * Redisson 客户端
     */
    private final RedissonClient redisson;

    /**
     * 构造函数
     *
     * @param redisson    Redisson 客户端
     * @param name        缓存名称
     * @param defaultTtl  默认 TTL 秒数
     */
    public RedissonCache(RedissonClient redisson, String name, long defaultTtl) {
        super(name, defaultTtl);
        this.redisson = redisson;
    }

    /**
     * 获取或创建 RMapCache
     */
    private RMapCache<Object, Object> getMapCache() {
        return redisson.getMapCache(getName());
    }

    /**
     * 获取缓存分组元数据 Set
     */
    private RSet<String> getCacheNamesSet() {
        return redisson.getSet(getCacheNamesMetaKey());
    }

    @Override
    protected String getCacheTypeForError() {
        return "redis";
    }

    @Override
    public String getType() {
        return TYPE;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T getNativeCache() {
        return (T) getMapCache();
    }

    // ==================== cacheName 元数据（Redis Set 持久化） ====================

    @Override
    protected void onPutCacheName(String cacheName) {
        if (cacheName != null && !cacheName.isEmpty()) {
            try {
                getCacheNamesSet().add(cacheName);
            } catch (Exception e) {
                // 元数据写入失败不影响主流程
            }
        }
    }

    @Override
    protected void doClearCacheNamesMeta() {
        try {
            getCacheNamesSet().delete();
        } catch (Exception e) {
            // 元数据清除失败不影响主流程
        }
    }

    @Override
    public Set<String> getCacheNames() {
        try {
            RSet<String> metaSet = getCacheNamesSet();
            if (!metaSet.isExists()) {
                return Collections.emptySet();
            }
            return new HashSet<>(metaSet.readAll());
        } catch (Exception e) {
            return Collections.emptySet();
        }
    }

    @Override
    public void clear(String... names) {
        if (names == null || names.length == 0) {
            return;
        }
        RSet<String> metaSet = getCacheNamesSet();
        RMapCache<Object, Object> mapCache = getMapCache();
        for (String cn : names) {
            if (cn == null || cn.trim().isEmpty()) {
                continue;
            }
            String prefix = buildKeyPrefix(cn);
            if (prefix.isEmpty()) {
                doClear();
                metaSet.delete();
                return;
            }
            // RMapCache 数据存储在 Redis Hash 中，不是独立 Redis key，
            // 因此不能使用 deleteByPattern（它在 Redis 键空间搜索）。
            // 改为遍历 mapCache 条目，删除匹配前缀的 key。
            for (Object entryKey : mapCache.keySet()) {
                if (entryKey instanceof String && ((String) entryKey).startsWith(prefix)) {
                    mapCache.remove(entryKey);
                }
            }
            // 从元数据集合中移除
            metaSet.remove(cn);
        }
    }

    // ==================== 缓存回源锁（Redisson RLock） ====================

    @Override
    protected boolean tryLock(String lockKey, long timeoutSeconds) {
        try {
            return redisson.getLock(lockKey).tryLock(0, timeoutSeconds, java.util.concurrent.TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    protected void unlock(String lockKey) {
        try {
            redisson.getLock(lockKey).unlock();
        } catch (Exception e) {
            // 锁释放失败不影响主流程
        }
    }

    // ==================== 内部实现 ====================

    @Override
    protected void doPutSafely(String key, Object value, long ttlSeconds) {
        getMapCache().put(key, value, ttlSeconds, java.util.concurrent.TimeUnit.SECONDS);
    }

    @Override
    @SuppressWarnings("unchecked")
    protected <T> T doGetSafely(String key) {
        return (T) getMapCache().get(key);
    }

    @Override
    protected void doEvictSafely(String key) {
        getMapCache().remove(key);
    }

    @Override
    protected void doClearSafely() {
        getMapCache().clear();
    }

    // ==================== TTL 操作 ====================

    @Override
    public boolean setTtl(String cacheName, String key, long ttlSeconds) {
        if (key == null) {
            return false;
        }
        String storageKey = buildStorageKey(cacheName, key);
        RMapCache<Object, Object> mapCache = getMapCache();
        if (!mapCache.containsKey(storageKey)) {
            return false;
        }
        String msg = "Failed to set TTL in redis: " + storageKey;
        return wrapException(msg, () -> {
            // 获取现有值并重新 put 以更新 TTL
            Object value = mapCache.get(storageKey);
            mapCache.put(storageKey, value, ttlSeconds, java.util.concurrent.TimeUnit.SECONDS);
            return true;
        });
    }

    @Override
    public long getTtl(String cacheName, String key) {
        if (key == null) {
            return -2;
        }
        String storageKey = buildStorageKey(cacheName, key);
        if (!getMapCache().containsKey(storageKey)) {
            return -2;
        }
        String msg = "Failed to get TTL from redis: " + storageKey;
        return wrapException(msg, () -> getMapCache().remainTimeToLive(storageKey) / 1000);
    }

}
