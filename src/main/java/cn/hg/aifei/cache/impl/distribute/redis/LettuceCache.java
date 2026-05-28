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

import cn.hg.aifei.cache.api.NullValue;
import cn.hg.aifei.cache.impl.distribute.AbstractDistributedCache;
import io.lettuce.core.RedisClient;
import io.lettuce.core.ScanArgs;
import io.lettuce.core.ScanCursor;
import io.lettuce.core.KeyScanCursor;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;
import io.lettuce.core.SetArgs;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Lettuce 缓存实现
 * <p>
 * 基于 Lettuce 的 Redis 缓存，支持：
 * <ul>
 *   <li>同步/异步/响应式 API</li>
 *   <li>连接池管理</li>
 *   <li>key 级别独立过期时间</li>
 *   <li>setTtl / getTtl 可选操作</li>
 * </ul>
 *
 * <p>缓存分组元数据使用 Redis Set 存储（key = namespace:cacheNames 或 cacheNames）。
 * 注意：evict 使用批量 DEL 提升性能，clear 使用 SCAN 避免 KEYS 阻塞。
 *
 * @author aifei
 */
public class LettuceCache extends AbstractDistributedCache {

    private static final String TYPE = "lettuce";

    /**
     * Lettuce RedisClient
     */
    private final RedisClient redisClient;

    /**
     * 连接（可复用）
     */
    private final StatefulRedisConnection<String, String> connection;

    /**
     * 构造函数
     *
     * @param redisClient Lettuce RedisClient
     * @param name        缓存名称
     * @param defaultTtl  默认 TTL 秒数
     */
    public LettuceCache(RedisClient redisClient, String name, long defaultTtl) {
        super(name, defaultTtl);
        this.redisClient = redisClient;
        this.connection = redisClient.connect();
    }

    private RedisCommands<String, String> sync() {
        return connection.sync();
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
        return (T) redisClient;
    }

    // ==================== cacheName 元数据（Redis Set 持久化） ====================

    @Override
    protected void onPutCacheName(String cacheName) {
        if (cacheName != null && !cacheName.isEmpty()) {
            try {
                sync().sadd(getCacheNamesMetaKey(), cacheName);
            } catch (Exception e) {
                // 元数据写入失败不影响主流程
            }
        }
    }

    @Override
    protected void doClearCacheNamesMeta() {
        try {
            sync().del(getCacheNamesMetaKey());
        } catch (Exception e) {
            // 元数据清除失败不影响主流程
        }
    }

    @Override
    public Set<String> getCacheNames() {
        try {
            return new LinkedHashSet<>(sync().smembers(getCacheNamesMetaKey()));
        } catch (Exception e) {
            return Collections.emptySet();
        }
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
                doClearCacheNamesMeta();
                return;
            }
            // 使用 SCAN 批量删除匹配前缀的 key
            deleteByPattern(prefix + "*");
            // 从元数据 Set 中移除
            sync().srem(getCacheNamesMetaKey(), cn);
        }
    }

    /**
     * 使用 Lettuce SCAN 命令批量删除匹配 pattern 的 key
     */
    private void deleteByPattern(String pattern) {
        ScanCursor cursor = ScanCursor.INITIAL;
        do {
            KeyScanCursor<String> keyCursor = sync().scan(
                    cursor,
                    ScanArgs.Builder.matches(pattern).limit(100)
            );
            List<String> keys = keyCursor.getKeys();
            if (keys != null && !keys.isEmpty()) {
                sync().del(keys.toArray(new String[0]));
            }
            cursor = keyCursor;
        } while (!cursor.isFinished());
    }

    // ==================== 缓存回源锁（SETNX） ====================

    @Override
    protected boolean tryLock(String lockKey, long timeoutSeconds) {
        try {
            // 使用 SET NX EX 原子操作（替代非原子的 SETNX + EXPIRE）
            SetArgs setArgs = SetArgs.Builder.nx().ex(timeoutSeconds);
            String result = sync().set(lockKey, "1", setArgs);
            return "OK".equals(result);
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    protected void unlock(String lockKey) {
        try {
            sync().del(lockKey);
        } catch (Exception e) {
            // 锁释放失败不影响主流程
        }
    }

    // ==================== 内部实现 ====================

    @Override
    protected void doPutSafely(String key, Object value, long ttlSeconds) {
        String strValue;
        if (value == NullValue.INSTANCE) {
            strValue = NullValue.STRING_MARKER;
        } else if (value instanceof String) {
            strValue = (String) value;
        } else {
            strValue = value.toString();
        }
        if (ttlSeconds > 0) {
            sync().setex(key, ttlSeconds, strValue);
        } else {
            sync().set(key, strValue);
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    protected <T> T doGetSafely(String key) {
        String result = sync().get(key);
        if (result == null) {
            return null;
        }
        if (NullValue.STRING_MARKER.equals(result)) {
            return (T) NullValue.INSTANCE;
        }
        return (T) result;
    }

    @Override
    protected void doEvictSafely(String key) {
        sync().del(key);
    }

    @Override
    protected void doClearSafely() {
        sync().flushdb();
    }

    /**
     * 批量删除（覆盖 AbstractCache 的 evict 模板，利用 Lettuce 的 varargs 批量删除能力）
     */
    @Override
    public void evict(String cacheName, String... keys) {
        if (keys == null || keys.length == 0) {
            return;
        }
        // 过滤 null key，构建有效存储键列表
        java.util.List<String> storageKeys = new java.util.ArrayList<>(keys.length);
        for (String k : keys) {
            if (k != null) {
                storageKeys.add(buildStorageKey(cacheName, k));
            }
        }
        if (storageKeys.isEmpty()) {
            return;
        }
        String msg = "Failed to evict from redis";
        wrapExceptionVoid(msg, () -> sync().del(storageKeys.toArray(new String[0])));
    }

    // ==================== TTL 操作 ====================

    @Override
    public boolean setTtl(String cacheName, String key, long ttlSeconds) {
        if (key == null) {
            return false;
        }
        String storageKey = buildStorageKey(cacheName, key);
        String msg = "Failed to set TTL in redis: " + storageKey;
        return wrapException(msg, () -> {
            if (sync().exists(storageKey) == 0) {
                return false;
            }
            return sync().expire(storageKey, ttlSeconds);
        });
    }

    @Override
    public long getTtl(String cacheName, String key) {
        if (key == null) {
            return -2;
        }
        String storageKey = buildStorageKey(cacheName, key);
        String msg = "Failed to get TTL from redis: " + storageKey;
        return wrapException(msg, () -> {
            if (sync().exists(storageKey) == 0) {
                return -2L;
            }
            long ttl = sync().ttl(storageKey);
            if (ttl == -1) {
                return -1L; // 永不过期
            }
            return ttl > 0 ? ttl : -2L;
        });
    }

}
