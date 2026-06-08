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

import static org.junit.Assert.*;

/**
 * CachePlugin 单元测试（缓存插件配置加载）
 */
public class CachePluginTest {

    private CachePlugin cachePlugin;

    @After
    public void tearDown() {
        if (cachePlugin != null) {
            try {
                cachePlugin.stop();
            } catch (Exception ignored) {
            }
        }
    }

    // ─── 正常场景 ───

    @Test
    public void testStart_LocalCache() {
        Prop prop = new Prop();
        prop.getProperties().setProperty("cache.type", "local");
        prop.getProperties().setProperty("cache.ttl", "60");

        cachePlugin = new CachePlugin(prop);
        cachePlugin.start();

        ICache cache = CacheManager.getCache(ICache.DEFAULT_CACHE_NAME);
        assertNotNull(cache);
        assertEquals("local", cache.getType());
        assertEquals("main", cache.getName());
    }

    @Test
    public void testStart_CaffeineCache() {
        Prop prop = new Prop();
        prop.getProperties().setProperty("cache.type", "caffeine");
        prop.getProperties().setProperty("cache.maxSize", "5000");
        prop.getProperties().setProperty("cache.ttl", "120");

        cachePlugin = new CachePlugin(prop);
        cachePlugin.start();

        ICache cache = CacheManager.getCache(ICache.DEFAULT_CACHE_NAME);
        assertNotNull(cache);
        assertEquals("caffeine", cache.getType());
        assertEquals("main", cache.getName());
    }

    @Test
    public void testStart_SetsNamespace() {
        Prop prop = new Prop();
        prop.getProperties().setProperty("cache.type", "local");
        prop.getProperties().setProperty("cache.ttl", "60");
        prop.getProperties().setProperty("cache.namespace", "custom-ns");

        cachePlugin = new CachePlugin(prop);
        cachePlugin.start();

        ICache cache = CacheManager.getCache(ICache.DEFAULT_CACHE_NAME);
        assertNotNull(cache);
        assertEquals("custom-ns", cache.getNamespace());
    }

    @Test
    public void testStart_SetsNullValue() {
        Prop prop = new Prop();
        prop.getProperties().setProperty("cache.type", "local");
        prop.getProperties().setProperty("cache.ttl", "60");
        prop.getProperties().setProperty("cache.nullValue", "true");

        cachePlugin = new CachePlugin(prop);
        cachePlugin.start();

        ICache cache = CacheManager.getCache(ICache.DEFAULT_CACHE_NAME);
        assertNotNull(cache);
        assertTrue(cache.isNullValue());
    }

    @Test
    public void testStart_NullValueDefaultFalse() {
        Prop prop = new Prop();
        prop.getProperties().setProperty("cache.type", "local");
        prop.getProperties().setProperty("cache.ttl", "60");
        // 不设置 cache.nullValue

        cachePlugin = new CachePlugin(prop);
        cachePlugin.start();

        ICache cache = CacheManager.getCache(ICache.DEFAULT_CACHE_NAME);
        assertNotNull(cache);
        assertFalse(cache.isNullValue());
    }

    @Test
    public void testStart_ExtensionCaches() {
        Prop prop = new Prop();
        prop.getProperties().setProperty("cache.type", "local");
        prop.getProperties().setProperty("cache.ttl", "60");
        // 扩展缓存
        prop.getProperties().setProperty("cache.names", "c1,c2");
        prop.getProperties().setProperty("cache.c1.type", "caffeine");
        prop.getProperties().setProperty("cache.c1.maxSize", "100");
        prop.getProperties().setProperty("cache.c1.ttl", "30");
        prop.getProperties().setProperty("cache.c2.type", "local");
        prop.getProperties().setProperty("cache.c2.ttl", "90");

        cachePlugin = new CachePlugin(prop);
        cachePlugin.start();

        // 主缓存
        ICache mainCache = CacheManager.getCache(ICache.DEFAULT_CACHE_NAME);
        assertNotNull(mainCache);
        assertEquals("local", mainCache.getType());

        // 扩展缓存
        ICache c1 = CacheManager.getCache("c1");
        assertNotNull(c1);
        assertEquals("caffeine", c1.getType());

        ICache c2 = CacheManager.getCache("c2");
        assertNotNull(c2);
        assertEquals("local", c2.getType());
    }

    @Test
    public void testStart_ExtensionCacheWithCustomNamespace() {
        Prop prop = new Prop();
        prop.getProperties().setProperty("cache.type", "local");
        prop.getProperties().setProperty("cache.ttl", "60");
        prop.getProperties().setProperty("cache.names", "c1");
        prop.getProperties().setProperty("cache.c1.type", "caffeine");
        prop.getProperties().setProperty("cache.c1.maxSize", "100");
        prop.getProperties().setProperty("cache.c1.ttl", "30");
        prop.getProperties().setProperty("cache.c1.namespace", "ext-ns");

        cachePlugin = new CachePlugin(prop);
        cachePlugin.start();

        ICache c1 = CacheManager.getCache("c1");
        assertNotNull(c1);
        assertEquals("ext-ns", c1.getNamespace());
    }

    @Test
    public void testStartAndStopCycle() {
        Prop prop = new Prop();
        prop.getProperties().setProperty("cache.type", "local");
        prop.getProperties().setProperty("cache.ttl", "60");

        cachePlugin = new CachePlugin(prop);
        cachePlugin.start();

        assertNotNull(CacheManager.getCache(ICache.DEFAULT_CACHE_NAME));

        cachePlugin.stop();

        // stop 后 CacheManager 被清空
        assertNull(CacheManager.getCache(ICache.DEFAULT_CACHE_NAME));
    }

    @Test
    public void testStartAndStop_MultipleCycles() {
        // 第一次
        Prop prop1 = new Prop();
        prop1.getProperties().setProperty("cache.type", "local");
        prop1.getProperties().setProperty("cache.ttl", "60");

        cachePlugin = new CachePlugin(prop1);
        cachePlugin.start();
        assertNotNull(CacheManager.getCache(ICache.DEFAULT_CACHE_NAME));
        cachePlugin.stop();
        assertNull(CacheManager.getCache(ICache.DEFAULT_CACHE_NAME));

        // 第二次
        Prop prop2 = new Prop();
        prop2.getProperties().setProperty("cache.type", "caffeine");
        prop2.getProperties().setProperty("cache.maxSize", "1000");
        prop2.getProperties().setProperty("cache.ttl", "30");

        cachePlugin = new CachePlugin(prop2);
        cachePlugin.start();
        ICache cache = CacheManager.getCache(ICache.DEFAULT_CACHE_NAME);
        assertNotNull(cache);
        assertEquals("caffeine", cache.getType());
        cachePlugin.stop();
    }

    // ─── 边界场景 ───

    @Test
    public void testStart_EmptyNamespace_DefaultsToAifei() {
        Prop prop = new Prop();
        prop.getProperties().setProperty("cache.type", "local");
        prop.getProperties().setProperty("cache.ttl", "60");
        // 不设置 namespace，默认为 "aifei"

        cachePlugin = new CachePlugin(prop);
        cachePlugin.start();

        ICache cache = CacheManager.getCache(ICache.DEFAULT_CACHE_NAME);
        assertEquals("aifei", cache.getNamespace());
    }

    @Test
    public void testGetCache_NonExistentName() {
        Prop prop = new Prop();
        prop.getProperties().setProperty("cache.type", "local");
        prop.getProperties().setProperty("cache.ttl", "60");

        cachePlugin = new CachePlugin(prop);
        cachePlugin.start();

        assertNull(CacheManager.getCache("non-existent"));
    }

    // ─── 异常场景 ───

    @Test(expected = Exception.class)
    public void testStart_NoTypeConfig_ThrowsException() {
        Prop prop = new Prop();
        // 不设置 cache.type
        cachePlugin = new CachePlugin(prop);
        cachePlugin.start();
    }

    @Test(expected = Exception.class)
    public void testStart_InvalidType_ThrowsException() {
        Prop prop = new Prop();
        prop.getProperties().setProperty("cache.type", "invalid-type");
        cachePlugin = new CachePlugin(prop);
        cachePlugin.start();
    }
}
