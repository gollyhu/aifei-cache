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
import cn.hg.aifei.cache.plugin.CachePlugin;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.Assert.*;

/**
 * LocalCache 单元测试（基于 ConcurrentHashMap 的本地缓存）
 */
public class LocalCacheTest {

    private CachePlugin cachePlugin;
    private ICache cache;

    @Before
    public void setUp() {
        Prop prop = new Prop();
        prop.getProperties().setProperty("cache.type", "local");
        prop.getProperties().setProperty("cache.ttl", "60");
        prop.getProperties().setProperty("cache.nullValue", "true");

        cachePlugin = new CachePlugin(prop);
        cachePlugin.start();

        cache = CacheManager.getCache(ICache.DEFAULT_CACHE_NAME);
    }

    @After
    public void tearDown() {
        if (cache != null) {
            cache.clearAll();
        }
        if (cachePlugin != null) {
            cachePlugin.stop();
        }
    }

    // ─── 正常场景 ───

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
        String result = cache.get("user", "supplier-key", () -> "hello");
        assertEquals("hello", result);
        // 二次调用从缓存中获取
        String cached = cache.get("user", "supplier-key");
        assertEquals("hello", cached);
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

    // ─── 边界场景 ───

    @Test
    public void testGet_NonExistentKey_ReturnsNull() {
        assertNull(cache.get("user", "no-such-key"));
    }

    @Test
    public void testGet_NonExistentCacheName_ReturnsNull() {
        // 使用不存在的缓存实例名：CacheManager.getCache 返回 null
        // 通过 CacheUtil.use 不会直接暴露，测试通过 get 调用但 cacheName 参数不影响 lookup
        // AbstractCache.get 中 cacheName 仅用于构造 storage key
        assertNull(cache.get("nonExistentNs", "some-key"));
    }

    @Test
    public void testGet_EmptyStringKey_ReturnsNull() {
        assertNull(cache.get("user", ""));
    }

    @Test
    public void testGet_NullKey_ReturnsNull() {
        // AbstractCache.get 对 null key 返回 null（isInvalidKey 防护）
        assertNull(cache.get("user", null));
    }

    @Test
    public void testPut_NullKey_NoOp() {
        // AbstractCache.put 对 null key 静默返回
        cache.put("user", null, "value");
        // 不会写入，get 应返回 null
        assertNull(cache.get("user", null));
    }

    @Test
    public void testPut_EmptyKey_NoOp() {
        cache.put("user", "", "value");
        assertNull(cache.get("user", ""));
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
        // 删除不存在的 key 应该不抛异常
        cache.evict("user", "no-such-key");
        assertNull(cache.get("user", "no-such-key"));
    }

    @Test
    public void testEvict_NullKeys_NoOp() {
        // evict(null) 应该不抛异常
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
    public void testTtlZero_NeverExpire() {
        cache.put("user", "ttl0-key", "value", 0);
        assertEquals("value", cache.get("user", "ttl0-key"));
    }

    @Test
    public void testTtlNegative() {
        // TTL 为负数时，LocalCache 中 expireTime = 0（永不过期），也可能直接过期。
        // 实际行为：expireTime = ttl <= 0 ? 0 : System.currentTimeMillis() + ttl * 1000
        // 当 ttl <= 0 时 expireTime = 0，Entity.isExpired() 判断 expireTime > 0 && now > expireTime
        // 由于 expireTime = 0，isExpired() 返回 false，所以永不过期
        cache.put("user", "ttl-neg-key", "value", -1);
        assertEquals("value", cache.get("user", "ttl-neg-key"));
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
    public void testGetName() {
        assertEquals("main", cache.getName());
    }

    @Test
    public void testGetType() {
        assertEquals("local", cache.getType());
    }

    @Test
    public void testGetNativeCache() {
        Object nativeCache = cache.getNativeCache();
        assertNotNull(nativeCache);
        assertTrue(nativeCache instanceof ConcurrentHashMap);
    }

    @Test
    public void testGetNamespace() {
        assertEquals("aifei", cache.getNamespace());
    }

    // ─── 异常场景 ───

    @Test
    public void testGetWithSupplier_SupplierReturnsNull() {
        // supplier 返回 null 且 nullValue=true 时会存入 null
        Object result = cache.get("user", "supplier-null-key", () -> null);
        assertNull(result);
        assertTrue(cache.exists("user", "supplier-null-key"));
    }

    // ─── nullValue 场景 ───

    @Test
    public void testNullValue_PutNullGetNull() {
        // nullValue 已通过配置设为 true，无需再调用 setNullValue
        cache.put("user", "null-key", null);
        assertNull(cache.get("user", "null-key"));
        assertTrue(cache.exists("user", "null-key"));
    }

    @Test
    public void testNullValue_IsNullValue() {
        assertTrue(cache.isNullValue());
    }
}
