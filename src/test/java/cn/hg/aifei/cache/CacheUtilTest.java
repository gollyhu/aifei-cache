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

import cn.aifei.log.LogKit;
import cn.aifei.util.Prop;
import cn.hg.aifei.cache.api.ICache;
import cn.hg.aifei.cache.core.CacheUtil;
import cn.hg.aifei.cache.plugin.CachePlugin;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.*;

import static org.junit.Assert.*;

/**
 * CacheUtil 单元测试（缓存工具类静态方法）
 */
public class CacheUtilTest {

    private CachePlugin cachePlugin;

    @BeforeClass
    public static void setUpLogFactory() {
        LogKit.get().setLogFactory(new DummyLogFactory());
    }

    @Before
    public void setUp() {
        Prop prop = new Prop();
        prop.getProperties().setProperty("cache.type", "local");
        prop.getProperties().setProperty("cache.ttl", "60");
        prop.getProperties().setProperty("cache.nullValue", "true");

        cachePlugin = new CachePlugin(prop);
        cachePlugin.start();
    }

    @After
    public void tearDown() {
        if (cachePlugin != null) {
            cachePlugin.stop();
        }
    }

    // ─── 正常场景 ───

    @Test
    public void testPutAndGet() {
        CacheUtil.put("user", "k1", "v1");
        assertEquals("v1", CacheUtil.get("user", "k1"));
    }

    @Test
    public void testPutWithTtl() {
        CacheUtil.put("user", "ttl-k", "ttl-v", 30);
        assertEquals("ttl-v", CacheUtil.get("user", "ttl-k"));
    }

    @Test
    public void testGetWithSupplier() {
        String result = CacheUtil.get("user", "supplier-k", () -> "supplier-v");
        assertEquals("supplier-v", result);
        assertEquals("supplier-v", CacheUtil.get("user", "supplier-k"));
    }

    @Test
    public void testGetWithSupplierAndTtl() {
        String result = CacheUtil.get("user", "supplier-ttl-k", () -> "world", 120);
        assertEquals("world", result);
        assertEquals("world", CacheUtil.get("user", "supplier-ttl-k"));
    }

    @Test
    public void testExists() {
        CacheUtil.put("user", "exist-k", "v");
        assertTrue(CacheUtil.exists("user", "exist-k"));
        assertFalse(CacheUtil.exists("user", "no-such-k"));
    }

    @Test
    public void testEvict() {
        CacheUtil.put("user", "evict-k", "v");
        CacheUtil.evict("user", "evict-k");
        assertNull(CacheUtil.get("user", "evict-k"));
    }

    @Test
    public void testClearAll() {
        CacheUtil.put("user", "k1", "v1");
        CacheUtil.put("user", "k2", "v2");
        CacheUtil.clearAll();
        assertNull(CacheUtil.get("user", "k1"));
        assertNull(CacheUtil.get("user", "k2"));
    }

    @Test
    public void testGetName() {
        assertEquals("main", CacheUtil.getName());
    }

    @Test
    public void testGetType() {
        assertEquals("local", CacheUtil.getType());
    }

    @Test
    public void testGetNativeCache() {
        assertNotNull(CacheUtil.getNativeCache());
    }

    @Test
    public void testGetNamespace() {
        assertEquals("aifei", CacheUtil.getNamespace());
    }

    @Test
    public void testGetDefaultTtl() {
        assertEquals(60, CacheUtil.getDefaultTtl());
    }

    @Test
    public void testSetDefaultTtl() {
        CacheUtil.setDefaultTtl(120);
        assertEquals(120, CacheUtil.getDefaultTtl());
    }

    @Test
    public void testUse_withName() {
        ICache cache = CacheUtil.use(ICache.DEFAULT_CACHE_NAME);
        assertNotNull(cache);
        assertEquals("main", cache.getName());

        ICache nullCache = CacheUtil.use("non-existent");
        assertNull(nullCache);
    }

    @Test
    public void testPutAll() {
        Map<String, Object> map = new HashMap<>();
        map.put("k1", "v1");
        map.put("k2", "v2");
        CacheUtil.putAll("user", map, 60);

        assertEquals("v1", CacheUtil.get("user", "k1"));
        assertEquals("v2", CacheUtil.get("user", "k2"));
    }

    @Test
    public void testGetAll() {
        CacheUtil.put("user", "k1", "v1");
        CacheUtil.put("user", "k2", "v2");
        CacheUtil.put("user", "k3", "v3");

        Set<String> keys = new LinkedHashSet<>();
        keys.add("k1");
        keys.add("k2");
        keys.add("no-such");

        Map<String, Object> result = CacheUtil.getAll("user", keys);
        assertEquals(2, result.size());
        assertEquals("v1", result.get("k1"));
        assertEquals("v2", result.get("k2"));
    }

    @Test
    public void testMultipleCacheOperations() {
        // 测试多次操作后的一致性
        CacheUtil.put("user", "k1", "v1");
        assertEquals("v1", CacheUtil.get("user", "k1"));

        CacheUtil.put("user", "k1", "v2");
        assertEquals("v2", CacheUtil.get("user", "k1"));

        CacheUtil.evict("user", "k1");
        assertNull(CacheUtil.get("user", "k1"));
    }

    // ─── 边界场景 ───

    @Test
    public void testGet_NullKey_ReturnsNull() {
        assertNull(CacheUtil.get("user", null));
    }

    @Test
    public void testGet_EmptyKey_ReturnsNull() {
        assertNull(CacheUtil.get("user", ""));
    }

    @Test
    public void testPut_NullKey_NoOp() {
        CacheUtil.put("user", null, "value");
    }

    @Test
    public void testPut_EmptyKey_NoOp() {
        CacheUtil.put("user", "", "value");
        assertNull(CacheUtil.get("user", ""));
    }

    // ─── nullValue 场景 ───

    @Test
    public void testNullValue_PutNullGetNull() {
        CacheUtil.put("user", "null-k", null);
        assertNull(CacheUtil.get("user", "null-k"));
        assertTrue(CacheUtil.exists("user", "null-k"));
    }
}
