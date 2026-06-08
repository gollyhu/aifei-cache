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

package cn.hg.aifei.cache.impl.distribute.redis;

import cn.hg.aifei.cache.api.NullValue;
import cn.hg.aifei.cache.impl.distribute.AbstractDistributedCache;
import cn.hg.aifei.cache.serializer.ICacheSerializer;
import redis.clients.jedis.RedisClient;
import redis.clients.jedis.params.SetParams;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Jedis 缓存实现
 * <p>
 * 基于 Jedis RedisClient 的 Redis 缓存，支持：
 * <ul>
 *   <li>连接池管理（通过 RedisClient 内置连接池）</li>
 *   <li>key 级别独立过期时间（SETEX / PSETEX）</li>
 *   <li>setTtl / getTtl 可选操作</li>
 *   <li>可选 ISerializer 序列化/反序列化</li>
 * </ul>
 *
 * <p>缓存分组元数据使用 Redis Set 存储（key = namespace:cacheNames 或 cacheNames）。
 * 注意：clear 使用 SCAN 遍历并删除匹配前缀的 key。
 *
 * @author aifei
 */
public class JedisCache extends AbstractDistributedCache {

    private static final String TYPE = "jedis";

    /**
     * NullValue 哨兵字节数组
     */
    private static final byte[] NULL_SENTINEL_BYTES =
            NullValue.STRING_MARKER.getBytes(StandardCharsets.UTF_8);

    /**
     * RedisClient 实例（内置连接池）
     */
    private final RedisClient client;

    /**
     * 序列化器
     */
    private final ICacheSerializer serializer;

    /**
     * 构造函数（无序列化器，仅支持 String 类型缓存值）
     *
     * @param client     RedisClient 实例
     * @param name       缓存名称
     * @param defaultTtl 默认 TTL 秒数
     */
    public JedisCache(RedisClient client, String name, long defaultTtl) {
        this(client, name, defaultTtl, null);
    }

    /**
     * 构造函数（带序列化器，支持任意 Java 对象的缓存存取）
     *
     * @param client     RedisClient 实例
     * @param name       缓存名称
     * @param defaultTtl 默认 TTL 秒数
     * @param serializer 序列化器，为 {@code null} 时退化为 toString() 模式
     */
    public JedisCache(RedisClient client, String name, long defaultTtl, ICacheSerializer serializer) {
        super(name, defaultTtl);
        this.client = client;
        this.serializer = serializer;
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
        return (T) client;
    }

    // ==================== cacheName 元数据（Redis Set 持久化） ====================

    @Override
    protected void onPutCacheName(String cacheName) {
        if (cacheName != null && !cacheName.isEmpty()) {
            try {
                client.sadd(getCacheNamesMetaKeyBytes(), cacheName.getBytes(StandardCharsets.UTF_8));
            } catch (Exception e) {
                // 元数据写入失败不影响主流程
            }
        }
    }

    @Override
    protected void doClearCacheNamesMeta() {
        try {
            client.del(getCacheNamesMetaKeyBytes());
        } catch (Exception e) {
            // 元数据清除失败不影响主流程
        }
    }

    private byte[] getCacheNamesMetaKeyBytes() {
        return getCacheNamesMetaKey().getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public Set<String> getCacheNames() {
        try {
            Set<byte[]> members = client.smembers(getCacheNamesMetaKeyBytes());
            if (members == null || members.isEmpty()) {
                return Collections.emptySet();
            }
            Set<String> result = new LinkedHashSet<>();
            for (byte[] member : members) {
                if (member != null) {
                    result.add(new String(member, StandardCharsets.UTF_8));
                }
            }
            return result;
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
            String pattern = prefix + "*";
            deleteByPattern(pattern);
            // 从元数据 Set 中移除
            client.srem(getCacheNamesMetaKeyBytes(), cn.getBytes(StandardCharsets.UTF_8));
        }
    }

    /**
     * 使用 keys 命令批量删除匹配 pattern 的 key
     * <p>注意：生产环境大量 key 时慎用 KEYS，推荐使用 RedissonCache 实现。</p>
     */
    private void deleteByPattern(String pattern) {
        try {
            java.util.Set<byte[]> keys = client.keys(pattern.getBytes(StandardCharsets.UTF_8));
            if (keys != null && !keys.isEmpty()) {
                client.del(keys.toArray(new byte[0][]));
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to delete keys by pattern: " + pattern, e);
        }
    }

    // ==================== 缓存回源锁（SETNX） ====================

    @Override
    protected boolean tryLock(String lockKey, long timeoutSeconds) {
        try {
            byte[] lockBytes = lockKey.getBytes(StandardCharsets.UTF_8);
            // 使用 SET NX EX 原子操作（替代过时的 SETNX + 非原子 EXPIRE）
            SetParams setParams = SetParams.setParams().nx().ex(timeoutSeconds);
            String result = client.set(lockBytes, new byte[0], setParams);
            return "OK".equals(result);
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    protected void unlock(String lockKey) {
        try {
            client.del(lockKey);
        } catch (Exception e) {
            // 锁释放失败不影响主流程
        }
    }

    // ==================== 内部实现 ====================

    @Override
    protected void doPutSafely(String key, Object value, long ttlSeconds) {
        byte[] keyBytes = key.getBytes(StandardCharsets.UTF_8);
        byte[] bytes;
        if (value == NullValue.INSTANCE) {
            bytes = NULL_SENTINEL_BYTES;
        } else {
            bytes = this.serializer.serialize(value);
        }
        if (ttlSeconds > 0) {
            client.setex(keyBytes, ttlSeconds, bytes);
        } else {
            client.set(keyBytes, bytes);
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    protected <T> T doGetSafely(String key) {
        byte[] bytes = client.get(key.getBytes(StandardCharsets.UTF_8));
        if (bytes == null) {
            return null;
        }
        if (Arrays.equals(NULL_SENTINEL_BYTES, bytes)) {
            return (T) NullValue.INSTANCE;
        }
        return (T) this.serializer.deserialize(bytes);
    }

    @Override
    protected void doEvictSafely(String key) {
        client.del(key);
    }

    @Override
    protected void doClearSafely() {
        client.flushDB();
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
            if (!client.exists(storageKey)) {
                return false;
            }
            client.expire(storageKey, ttlSeconds);
            return true;
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
            if (!client.exists(storageKey)) {
                return -2L;
            }
            long ttl = client.ttl(storageKey);
            if (ttl == -1) {
                return -1L; // 永不过期
            }
            return ttl > 0 ? ttl : -2L;
        });
    }

}
