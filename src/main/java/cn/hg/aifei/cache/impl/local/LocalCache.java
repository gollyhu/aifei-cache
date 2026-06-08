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

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 本地缓存实现
 * <p>
 * 基于 ConcurrentHashMap，支持 TTL 过期。
 * 采用双重过期策略：
 * <ul>
 *   <li><b>惰性删除</b>：每次访问时检查是否过期</li>
 *   <li><b>后台清理</b>：定期扫描清理过期条目</li>
 * </ul>
 *
 * <p>适用场景：
 * <ul>
 *   <li>单机部署的本地缓存</li>
 *   <li>需要简单 TTL 语义的缓存</li>
 *   <li>作为 Redisson 的降级方案</li>
 * </ul>
 *
 * @author aifei
 */
public class LocalCache extends AbstractCache {

    private static final String TYPE = "local";

    /**
     * 缓存条目实体
     */
    public static class Entity {
        private final Object value;
        private final long expireTime; // 过期时间戳（毫秒），0 表示永不过期

        public Entity(Object value, long expireTime) {
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

    /**
     * 原生 ConcurrentHashMap
     */
    private final ConcurrentHashMap<String, Entity> map;

    /**
     * 已记录的 cacheName 集合（本地内存，线程安全）
     */
    private final Set<String> cacheNames = ConcurrentHashMap.newKeySet();

    /**
     * 后台清理线程是否运行
     */
    private final AtomicBoolean running = new AtomicBoolean(false);

    /**
     * 清理线程执行器
     */
    private ScheduledExecutorService cleaner;

    /**
     * 清理间隔（秒）
     */
    private static final long CLEAN_INTERVAL_SECONDS = 60;

    /**
     * 构造函数
     *
     * @param name        缓存名称
     * @param map         ConcurrentHashMap 实例
     * @param defaultTtl  默认 TTL 秒数
     */
    public LocalCache(String name, ConcurrentHashMap<String, Entity> map, long defaultTtl) {
        super(name, defaultTtl);
        this.map = map != null ? map : new ConcurrentHashMap<>();
        startCleaner();
    }

    /**
     * 启动后台清理线程
     */
    private void startCleaner() {
        if (running.compareAndSet(false, true)) {
            cleaner = Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "LocalCache-Cleaner-" + getName());
                t.setDaemon(true);
                return t;
            });
            cleaner.scheduleAtFixedRate(this::cleanExpired, CLEAN_INTERVAL_SECONDS, CLEAN_INTERVAL_SECONDS, TimeUnit.SECONDS);
        }
    }

    /**
     * 停止后台清理线程
     */
    private void stopCleaner() {
        if (running.compareAndSet(true, false)) {
            if (cleaner != null) {
                cleaner.shutdown();
                try {
                    if (!cleaner.awaitTermination(5, TimeUnit.SECONDS)) {
                        cleaner.shutdownNow();
                    }
                } catch (InterruptedException e) {
                    cleaner.shutdownNow();
                    Thread.currentThread().interrupt();
                }
            }
        }
    }

    /**
     * 清理过期条目
     */
    private void cleanExpired() {
        long now = System.currentTimeMillis();
        for (Map.Entry<String, Entity> entry : map.entrySet()) {
            if (entry.getValue().isExpired()) {
                map.remove(entry.getKey(), entry.getValue());
            }
        }
    }

    @Override
    public String getType() {
        return TYPE;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T getNativeCache() {
        return (T) map;
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
            // 遍历所有 key，清除匹配前缀的条目
            for (String key : map.keySet()) {
                if (key.startsWith(prefix)) {
                    map.remove(key);
                }
            }
            cacheNames.remove(cn);
        }
    }

    // ==================== 内部实现 ====================

    @Override
    protected void doPut(String key, Object value, long ttlSeconds) {
        long expireTime = ttlSeconds > 0
                ? System.currentTimeMillis() + ttlSeconds * 1000
                : 0; // 0 表示永不过期
        map.put(key, new Entity(value, expireTime));
    }

    @Override
    @SuppressWarnings("unchecked")
    protected <T> T doGet(String key) {
        Entity entity = map.get(key);
        if (entity == null) {
            return null;
        }
        // 惰性删除：检查是否过期
        if (entity.isExpired()) {
            map.remove(key, entity);
            return null;
        }
        return (T) entity.getValue();
    }

    @Override
    protected void doEvict(String key) {
        map.remove(key);
    }

    @Override
    protected void doClear() {
        map.clear();
    }

    // ==================== TTL 操作 ====================

    @Override
    public boolean setTtl(String cacheName, String key, long ttlSeconds) {
        if (key == null) {
            return false;
        }
        String storageKey = buildStorageKey(cacheName, key);
        Entity entity = map.get(storageKey);
        if (entity == null) {
            return false;
        }
        long expireTime = ttlSeconds > 0
                ? System.currentTimeMillis() + ttlSeconds * 1000
                : 0;
        map.put(storageKey, new Entity(entity.getValue(), expireTime));
        return true;
    }

    @Override
    public long getTtl(String cacheName, String key) {
        if (key == null) {
            return -2;
        }
        String storageKey = buildStorageKey(cacheName, key);
        Entity entity = map.get(storageKey);
        if (entity == null) {
            return -2;
        }
        if (entity.getExpireTime() == 0) {
            return -1; // 永不过期
        }
        long remaining = entity.getExpireTime() - System.currentTimeMillis();
        return remaining > 0 ? remaining / 1000 : -2;
    }

    // ==================== 生命周期 ====================

    /**
     * 关闭缓存，释放资源
     */
    public void shutdown() {
        stopCleaner();
        clearAll();
    }

    /**
     * 获取当前缓存条目数
     */
    public int size() {
        return map.size();
    }
}
