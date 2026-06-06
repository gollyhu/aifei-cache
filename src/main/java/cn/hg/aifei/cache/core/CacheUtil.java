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

package cn.hg.aifei.cache.core;

import cn.hg.aifei.cache.api.ICache;

import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

/**
 * 缓存工具类
 * <p>
 * 提供静态方法访问缓存，默认使用默认缓存实例。
 * 使用前请先调用 {@link CacheManager#registerCache(String, ICache)} 注册缓存实例。
 *
 * <h3>新 API（带 cacheName 分组参数）</h3>
 * 所有数据操作方法都包含 cacheName 参数作为第一个参数，
 * 用于指定缓存分组。旧的无 cacheName 方法已标记为 {@code @Deprecated}。
 *
 * @author aifei
 */
public final class CacheUtil {

    private static ICache DEFAULT_CACHE;

    private CacheUtil() {
    }

    /**
     * 获取默认缓存
     * @return ICache 实例
     */
    private static ICache use() {
        if (DEFAULT_CACHE == null) {
            DEFAULT_CACHE = CacheManager.getCache(ICache.DEFAULT_CACHE_NAME);
        }

        return DEFAULT_CACHE;
    }

    /**
     * 使用指定缓存
     * @param name 缓存实例名称
     * @return ICache 实例
     */
    public static ICache use(String name) {
        return CacheManager.getCache(name);
    }

    // ==================== 新 API：带 cacheName 参数 ====================

    /**
     * 存入缓存，使用默认 TTL
     *
     * @param cacheName 缓存分组名称
     * @param key       缓存 key
     * @param value     缓存值
     */
    public static void put(String cacheName, String key, Object value) {
        use().put(cacheName, key, value);
    }

    /**
     * 存入缓存，指定 TTL（秒）
     *
     * @param cacheName  缓存分组名称
     * @param key        缓存 key
     * @param value      缓存值
     * @param ttlSeconds TTL 秒数
     */
    public static void put(String cacheName, String key, Object value, long ttlSeconds) {
        use().put(cacheName, key, value, ttlSeconds);
    }

    /**
     * 获取缓存值
     *
     * @param cacheName 缓存分组名称
     * @param key       缓存 key
     * @return 缓存值，不存在返回 null
     */
    public static <T> T get(String cacheName, String key) {
        return use().get(cacheName, key);
    }

    /**
     * 检查指定缓存键是否存在
     *
     * @param cacheName 缓存分组名称
     * @param key       缓存 key
     * @return true 表示键存在，false 表示键不存在或参数无效
     */
    public static boolean exists(String cacheName, String key) {
        return use().exists(cacheName, key);
    }

    /**
     * 缓存回源：如果缓存不存在，则调用 supplier 获取值并缓存
     *
     * @param cacheName  缓存分组名称
     * @param key        缓存 key
     * @param supplier   值提供器
     * @param ttlSeconds TTL 秒数
     * @return 缓存值或新计算的值
     */
    public static <T> T get(String cacheName, String key, Supplier<T> supplier, long ttlSeconds) {
        return use().get(cacheName, key, supplier, ttlSeconds);
    }

    /**
     * 缓存回源，使用默认 TTL
     *
     * @param cacheName 缓存分组名称
     * @param key       缓存 key
     * @param supplier  值提供器
     * @return 缓存值或新计算的值
     */
    public static <T> T get(String cacheName, String key, Supplier<T> supplier) {
        return use().get(cacheName, key, supplier);
    }

    // ---------- 批量操作（新 API）----------

    /**
     * 批量存入缓存
     *
     * @param cacheName  缓存分组名称
     * @param map        键值对
     * @param ttlSeconds TTL 秒数
     */
    public static void putAll(String cacheName, Map<String, Object> map, long ttlSeconds) {
        use().putAll(cacheName, map, ttlSeconds);
    }

    /**
     * 批量获取缓存值
     *
     * @param cacheName 缓存分组名称
     * @param keys      缓存 key 集合
     * @return 存在的键值对
     */
    public static Map<String, Object> getAll(String cacheName, Collection<String> keys) {
        return use().getAll(cacheName, keys);
    }

    // ---------- 删除（新 API）----------

    /**
     * 删除指定缓存分组下的条目，支持可变参数
     *
     * @param cacheName 缓存分组名称
     * @param keys      要删除的 key
     */
    public static void evict(String cacheName, String... keys) {
        use().evict(cacheName, keys);
    }

    // ---------- 生命周期 ----------

    /**
     * 清空所有缓存
     */
    public static void clearAll() {
        use().clearAll();
    }

    /**
     * 获取缓存名称
     */
    public static String getName() {
        return use().getName();
    }

    /**
     * 获取缓存类型
     */
    public static String getType() {
        return use().getType();
    }

    // ---------- 原生实例（逃生口）----------

    /**
     * 获取原生缓存实例
     * <p>
     * CaffeineCache 返回 com.github.benmanes.caffeine.cache.Cache
     * RedissonCache 返回 org.redisson.api.RMapCache
     * LocalCache 返回 java.util.concurrent.ConcurrentHashMap
     */
    public static <T> T getNativeCache() {
        return use().getNativeCache();
    }

    // ---------- 可选 TTL 操作（新 API）----------

    /**
     * 设置缓存分组下某个 key 的过期时间（可选操作）
     *
     * @param cacheName  缓存分组名称
     * @param key        缓存 key
     * @param ttlSeconds TTL 秒数
     * @return 是否成功
     */
    public static boolean setTtl(String cacheName, String key, long ttlSeconds) {
        return use().setTtl(cacheName, key, ttlSeconds);
    }

    /**
     * 获取缓存分组下某个 key 的剩余 TTL（可选操作）
     *
     * @param cacheName 缓存分组名称
     * @param key       缓存 key
     * @return 剩余秒数，-1 表示永久，-2 表示不存在
     */
    public static long getTtl(String cacheName, String key) {
        return use().getTtl(cacheName, key);
    }

    // ---------- 缓存分组元数据 ----------

    /**
     * 获取当前缓存实例中所有已记录的 cacheName
     *
     * @return cacheName 集合
     */
    public static Set<String> getCacheNames() {
        return use().getCacheNames();
    }

    /**
     * 清除指定 cacheName 下的所有缓存条目
     *
     * @param cacheNames 要清除的缓存分组名称
     */
    public static void clear(String... cacheNames) {
        use().clear(cacheNames);
    }

    // ---------- 命名空间 ----------

    /**
     * 获取当前缓存实例的命名空间
     *
     * @return namespace
     */
    public static String getNamespace() {
        return use().getNamespace();
    }

    // ---------- 默认 TTL ----------

    /**
     * 获取默认 TTL
     */
    public static long getDefaultTtl() {
        return use().getDefaultTtl();
    }

    /**
     * 设置默认 TTL
     */
    public static void setDefaultTtl(long ttlSeconds) {
        use().setDefaultTtl(ttlSeconds);
    }


    public static boolean isNullValue(){
        return use().isNullValue();
    }
}
