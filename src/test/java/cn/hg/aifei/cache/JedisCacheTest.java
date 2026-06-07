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

package cn.hg.aifei.cache;

import cn.aifei.util.Prop;
import cn.hg.aifei.cache.api.ICache;
import cn.hg.aifei.cache.core.CacheManager;
import cn.hg.aifei.cache.impl.distribute.AbstractDistributedCache;
import cn.hg.aifei.cache.plugin.CachePlugin;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.*;

/**
 * JedisCache 单元测试（基于 Jedis RedisClient 的分布式缓存）
 */
public class JedisCacheTest {

    /**
     * 内存版 AbstractDistributedCache，用于测试 get(Supplier) 的分布式缓存回源逻辑，
     * 无需依赖外部 Redis 服务器。
     */
    static class InMemoryDistributedCache extends AbstractDistributedCache {
        private final Map<String, Object> store = new ConcurrentHashMap<>();
        private final AtomicBoolean lockHeld = new AtomicBoolean(false);

        InMemoryDistributedCache(String name, long defaultTtl) {
            super(name, defaultTtl);
        }

        @Override
        protected String getCacheTypeForError() {
            return "memory";
        }

        @Override
        protected boolean tryLock(String lockKey, long timeoutSeconds) {
            return lockHeld.compareAndSet(false, true);
        }

        @Override
        protected void unlock(String lockKey) {
            lockHeld.set(false);
        }

        @Override
        protected void doPutSafely(String key, Object value, long ttlSeconds) {
            store.put(key, value);
        }

        @Override
        @SuppressWarnings("unchecked")
        protected <T> T doGetSafely(String key) {
            return (T) store.get(key);
        }

        @Override
        protected void doEvictSafely(String key) {
            store.remove(key);
        }

        @Override
        protected void doClearSafely() {
            store.clear();
        }

        @Override
        public Set<String> getCacheNames() {
            return new LinkedHashSet<>(store.keySet());
        }

        @Override
        public String getType() {
            return "memory";
        }

        @Override
        public void clear(String... cacheNames) {
            store.clear();
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> T getNativeCache() {
            return (T) store;
        }
    }

    private CachePlugin cachePlugin;
    private ICache cache;

    @Before
    public void setUp() {
        Prop prop = new Prop();
        prop.getProperties().setProperty("cache.type", "jedis");
        prop.getProperties().setProperty("cache.address", "127.0.0.1:6379");
        prop.getProperties().setProperty("cache.password", "");
        prop.getProperties().setProperty("cache.database", "0");
        prop.getProperties().setProperty("cache.ttl", "60");
        prop.getProperties().setProperty("cache.nullValue", "true");
        prop.getProperties().setProperty("cache.serializer", "jackson");

        cachePlugin = new CachePlugin(prop);
        cachePlugin.start();

        cache = CacheManager.getCache(ICache.DEFAULT_CACHE_NAME);
    }

    @After
    public void tearDown() {
        if (cache != null) {
            try {
                cache.clearAll();
            } catch (Exception ignored) {
            }
        }
        if (cachePlugin != null) {
            try {
                cachePlugin.stop();
            } catch (Exception ignored) {
            }
        }
    }

    // ==================== 正常场景 ====================

    @Test
    public void testPutAndGet_String() {
        cache.put("user", "key1", "hello");
        assertEquals("hello", cache.get("user", "key1"));
    }

    @Test
    public void testPutAndGet_Integer() {
        cache.put("user", "key1", 123);
        assertEquals((Object) 123, cache.get("user", "key1"));
    }

    @Test
    public void testPutAndGet_Long() {
        cache.put("user", "key1", 999L);
        assertEquals(Long.valueOf(999L), cache.get("user", "key1"));
    }

    @Test
    public void testPutAndGet_Double() {
        cache.put("user", "key1", 3.14);
        assertEquals(Double.valueOf(3.14), cache.get("user", "key1"));
    }

    @Test
    public void testPutAndGet_Boolean() {
        cache.put("user", "key1", true);
        assertEquals(true, cache.get("user", "key1"));
    }

    @Test
    public void testPutAndGet_POJO() {
        Map<String, Object> pojo = new HashMap<>();
        pojo.put("name", "test");
        pojo.put("age", 18);
        cache.put("user", "pojo-key", pojo);
        assertEquals(pojo, cache.get("user", "pojo-key"));
    }

    @Test
    public void testPutAndGet_List() {
        List<String> list = Arrays.asList("a", "b", "c");
        cache.put("user", "list-key", list);
        assertEquals(list, cache.get("user", "list-key"));
    }

    @Test
    public void testGetWithSupplier() {
        InMemoryDistributedCache testCache = new InMemoryDistributedCache("test-cache", 60);
        testCache.setNullValue(true);

        // 首次调用：key 不存在，supplier 应被执行并缓存结果
        String result = testCache.get("user", "supplier-key", () -> "hello");
        assertEquals("hello", result);

        // 二次调用：从缓存中读取，supplier 不应再执行
        assertEquals("hello", testCache.get("user", "supplier-key"));
        assertTrue(testCache.exists("user", "supplier-key"));
    }

    @Test
    public void testGetWithSupplierAndTtl() {
        String result = cache.get("user", "supplier-ttl-key", () -> "world", 120);
        assertEquals("world", result);
        assertEquals("world", cache.get("user", "supplier-ttl-key"));
    }

    @Test
    public void testOverwriteExistingKey() {
        cache.put("user", "ow-key", "first");
        assertEquals("first", cache.get("user", "ow-key"));
        cache.put("user", "ow-key", "second");
        assertEquals("second", cache.get("user", "ow-key"));
    }

    @Test
    public void testSpecifyTtl() {
        cache.put("user", "ttl-key", "value", 10);
        assertEquals("value", cache.get("user", "ttl-key"));
    }

    @Test
    public void testGetName() {
        assertEquals("main", cache.getName());
    }

    @Test
    public void testGetType() {
        assertEquals("jedis", cache.getType());
    }

    @Test
    public void testGetDefaultTtl() {
        assertEquals(60, cache.getDefaultTtl());
    }

    @Test
    public void testSetDefaultTtl() {
        cache.setDefaultTtl(120);
        assertEquals(120, cache.getDefaultTtl());
    }

    @Test
    public void testGetNamespace() {
        assertEquals("aifei", cache.getNamespace());
    }

    @Test
    public void testGetNativeCache() {
        // InMemoryDistributedCache 内存测试：验证 getNativeCache 返回底层存储
        InMemoryDistributedCache testCache = new InMemoryDistributedCache("native-test", 60);
        Object nativeCache = testCache.getNativeCache();
        assertNotNull("nativeCache 不应为 null", nativeCache);
        assertTrue("InMemoryDistributedCache 应返回 ConcurrentHashMap",
                nativeCache instanceof ConcurrentHashMap);

        // Jedis 场景：验证类型和可用性
        Object jedisNative = cache.getNativeCache();
        assertNotNull("Jedis nativeCache 不应为 null", jedisNative);
        assertTrue("JedisCache 应返回 RedisClient",
                jedisNative instanceof redis.clients.jedis.RedisClient);
    }

    // ==================== 批量操作 ====================

    @Test
    public void testPutAll() {
        Map<String, Object> map = new HashMap<>();
        map.put("k1", "v1");
        map.put("k2", "v2");
        cache.putAll("user", map, 60);

        assertEquals("v1", cache.get("user", "k1"));
        assertEquals("v2", cache.get("user", "k2"));
    }

    @Test
    public void testGetAll() {
        cache.put("user", "k1", "v1");
        cache.put("user", "k2", "v2");
        cache.put("user", "k3", "v3");

        List<String> keys = Arrays.asList("k1", "k2", "no-such");
        Map<String, Object> result = cache.getAll("user", keys);
        assertEquals(2, result.size());
        assertEquals("v1", result.get("k1"));
        assertEquals("v2", result.get("k2"));
    }

    // ==================== 边界场景 ====================

    @Test
    public void testGet_NonExistentKey_ReturnsNull() {
        assertNull(cache.get("user", "no-such-key"));
    }

    @Test
    public void testGet_NullKey_ReturnsNull() {
        assertNull(cache.get("user", null));
    }

    @Test
    public void testGet_EmptyKey_ReturnsNull() {
        assertNull(cache.get("user", ""));
    }

    @Test
    public void testPut_NullKey_NoOp() {
        cache.put("user", null, "value");
        assertNull(cache.get("user", null));
    }

    @Test
    public void testPut_EmptyKey_NoOp() {
        cache.put("user", "", "value");
        assertNull(cache.get("user", ""));
    }

    @Test
    public void testEmptyStringValue() {
        cache.put("user", "empty-key", "");
        assertEquals("", cache.get("user", "empty-key"));
    }

    @Test
    public void testTtlZero() {
        cache.put("user", "ttl0-key", "value", 0);
        assertEquals("value", cache.get("user", "ttl0-key"));
    }

    @Test
    public void testExists_NonExistentKey_ReturnsFalse() {
        assertFalse(cache.exists("user", "no-such-key"));
    }

    @Test
    public void testExists_ExistingKey_ReturnsTrue() {
        cache.put("user", "exist-key", "value");
        assertTrue(cache.exists("user", "exist-key"));
    }

    @Test
    public void testEvict_ExistingKey() {
        cache.put("user", "evict-key", "value");
        cache.evict("user", "evict-key");
        assertNull(cache.get("user", "evict-key"));
    }

    @Test
    public void testEvict_NonExistentKey_NoOp() {
        cache.evict("user", "no-such-key");
        assertNull(cache.get("user", "no-such-key"));
    }

    @Test
    public void testEvict_NullKeys_NoOp() {
        cache.evict("user", (String[]) null);
        cache.evict("user");
    }

    @Test
    public void testClearAll() {
        cache.put("user", "k1", "v1");
        cache.put("user", "k2", "v2");
        cache.clearAll();
        assertNull(cache.get("user", "k1"));
        assertNull(cache.get("user", "k2"));
    }

    @Test
    public void testMultipleCacheNames() {
        cache.put("user", "k1", "v1");
        cache.put("order", "k2", "v2");
        assertEquals("v1", cache.get("user", "k1"));
        assertEquals("v2", cache.get("order", "k2"));
    }

    // ==================== 缓存分组元数据 ====================

    @Test
    public void testGetCacheNames() {
        cache.put("user", "k1", "v1");
        cache.put("order", "k1", "v1");

        Set<String> names = cache.getCacheNames();
        assertNotNull(names);
        assertTrue(names.contains("user"));
        assertTrue(names.contains("order"));
    }

    @Test
    public void testClearByCacheName() {
        cache.put("user", "k1", "v1");
        cache.put("user", "k2", "v2");
        cache.put("order", "k1", "v1");

        cache.clear("user");

        assertNull(cache.get("user", "k1"));
        assertNull(cache.get("user", "k2"));
        // order 未被清除
        assertEquals("v1", cache.get("order", "k1"));
    }

    // ==================== TTL 操作 ====================

    @Test
    public void testSetTtl() {
        cache.put("user", "ttl-key", "value", 300);
        boolean result = cache.setTtl("user", "ttl-key", 120);
        assertTrue(result);
    }

    @Test
    public void testSetTtl_NullKey_ReturnsFalse() {
        assertFalse(cache.setTtl("user", null, 120));
    }

    @Test
    public void testSetTtl_NonExistentKey() {
        boolean result = cache.setTtl("user", "no-such-key", 120);
        assertFalse(result);
    }

    @Test
    public void testGetTtl_ExistingKey() {
        cache.put("user", "ttl-key", "value", 300);
        long ttl = cache.getTtl("user", "ttl-key");
        assertTrue("TTL should be > 0, but was: " + ttl, ttl > 0);
    }

    @Test
    public void testGetTtl_NonExistentKey() {
        long ttl = cache.getTtl("user", "no-such-key");
        assertEquals(-2, ttl);
    }

    @Test
    public void testGetTtl_NullKey() {
        long ttl = cache.getTtl("user", null);
        assertEquals(-2, ttl);
    }

    // ==================== 异常场景 ====================

    @Test
    public void testGetWithSupplier_SupplierReturnsNull() {
        Object result = cache.get("user", "supplier-null-key", () -> null);
        assertNull(result);
        assertTrue(cache.exists("user", "supplier-null-key"));
    }

    // ==================== nullValue 场景 ====================

    @Test
    public void testNullValue_PutNullGetNull() {
        cache.put("user", "null-key", null);
        assertNull(cache.get("user", "null-key"));
        assertTrue(cache.exists("user", "null-key"));
    }

    @Test
    public void testNullValue_IsNullValue() {
        assertTrue(cache.isNullValue());
    }

    @Test
    public void testNullValue_GetWithSupplier_DoesNotPenetrate() {
        cache.put("user", "null-key", null);
        String result = cache.get("user", "null-key", () -> "should-not-be-called");
        assertNull(result);
    }
}
