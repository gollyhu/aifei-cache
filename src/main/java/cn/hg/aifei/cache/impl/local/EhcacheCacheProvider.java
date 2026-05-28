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

package cn.hg.aifei.cache.impl.local;

import cn.aifei.util.Prop;
import cn.hg.aifei.cache.api.ICacheProvider;
import cn.hg.aifei.cache.api.ICache;
import org.ehcache.Cache;
import org.ehcache.CacheManager;
import org.ehcache.config.builders.CacheConfigurationBuilder;
import org.ehcache.config.builders.CacheManagerBuilder;
import org.ehcache.config.builders.ResourcePoolsBuilder;
import org.ehcache.config.units.EntryUnit;
import org.ehcache.config.units.MemoryUnit;

/**
 * Ehcache 缓存提供者
 * <p>
 * 根据配置创建 EhcacheCache 实例，管理 Ehcache CacheManager 生命周期。
 *
 * <p>配置参数：
 * <ul>
 *   <li>&lt;prefix&gt;heapEntries - 堆内最大条目数（默认 10000）</li>
 *   <li>&lt;prefix&gt;ttl - 默认 TTL 秒数（默认 3600）</li>
 *   <li>&lt;prefix&gt;offHeapMB - 堆外内存大小 MB（可选，默认不启用）</li>
 * </ul>
 *
 * @author aifei
 */
public class EhcacheCacheProvider implements ICacheProvider {

    private static final String TYPE = "ehcache";

    /** 默认堆内最大条目数 */
    private static final long DEFAULT_HEAP_ENTRIES = 10000;

    /** 默认 TTL：1小时 */
    private static final long DEFAULT_TTL = 3600;

    /** 本 Provider 管理的 CacheManager */
    private CacheManager managedCacheManager;

    /** 本 Provider 管理的 Cache */
    private Cache<String, Object> managedCache;

    @Override
    public String getType() {
        return TYPE;
    }

    @Override
    public ICache buildCache(String name, String type, Prop prop) {
        return buildCache(name, type, prop, "cache." + name + ".");
    }

    @Override
    public ICache buildCache(String name, String type, Prop prop, String prefix) {
        long heapEntries = prop.getLong(prefix + "heapEntries", DEFAULT_HEAP_ENTRIES);
        long ttl = prop.getLong(prefix + "ttl", DEFAULT_TTL);
        long offHeapMB = prop.getLong(prefix + "offHeapMB", 0L);

        // 构建资源池
        ResourcePoolsBuilder resourcePoolsBuilder = ResourcePoolsBuilder.newResourcePoolsBuilder()
                .heap(heapEntries, EntryUnit.ENTRIES);

        if (offHeapMB > 0) {
            resourcePoolsBuilder = resourcePoolsBuilder.offheap(offHeapMB, MemoryUnit.MB);
        }

        // 构建 CacheManager（不设 cache-level expiry，由 EhcacheCache 的 TtlEntry 处理 per-entry TTL）
        managedCacheManager = CacheManagerBuilder.newCacheManagerBuilder()
                .withCache(name,
                        CacheConfigurationBuilder.newCacheConfigurationBuilder(
                                        String.class, Object.class, resourcePoolsBuilder))
                .build(true);

        // 获取 Cache
        managedCache = managedCacheManager.getCache(name, String.class, Object.class);

        return new EhcacheCache(managedCache, name, ttl);
    }

    /**
     * 获取管理的 CacheManager
     */
    public CacheManager getManagedCacheManager() {
        return managedCacheManager;
    }

    /**
     * 获取管理的 Cache
     */
    public Cache<String, Object> getManagedCache() {
        return managedCache;
    }

    /**
     * 关闭 CacheManager
     */
    public void shutdown() {
        if (managedCacheManager != null) {
            try {
                managedCacheManager.close();
            } catch (Exception e) {
                throw new RuntimeException("Failed to close Ehcache CacheManager", e);
            }
        }
    }
}
