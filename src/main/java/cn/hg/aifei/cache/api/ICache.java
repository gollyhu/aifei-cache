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

package cn.hg.aifei.cache.api;

import cn.aifei.util.StrUtil;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

/**
 * 缓存接口 - 定义缓存的原子操作
 * <p>
 * CaffeineCache、RedissonCache、LocalCache 直接实现此接口。
 * 通过 CacheProvider 创建实例，由 CacheManager 统一管理。
 *
 * <h3>命名空间与缓存分组</h3>
 * 所有数据操作方法（put/get/evict等）的第一个参数为 cacheName（缓存分组），
 * 实际存储键按以下规则拼接：
 * <ul>
 *   <li>若 namespace 不为空：{@code namespace + ":" + cacheName + ":" + key}</li>
 *   <li>若 namespace 为  空：{@code cacheName + ":" + key}</li>
 * </ul>
 *
 * @author aifei
 */
public interface ICache {
    String DEFAULT_CACHE_NAME = "main";

    /**
     * 命名空间
     */
    String DEFAULT_NAMESPACE = "aifei";

    /**
     * 根据 namespace、cacheName、key 生成实际存储键
     *
     * @param cacheName 缓存分组名称
     * @param key       业务 key
     * @return 存储键
     */
    default String buildStorageKey(String cacheName, String key) {
        if (StrUtil.isBlank(cacheName)) {
            throw new IllegalArgumentException("cacheName must not be null or blank");
        }
        if (StrUtil.isBlank(key)) {
            throw new IllegalArgumentException("key must not be null or blank");
        }

        if (!StrUtil.isBlank(getNamespace())) {
            return getNamespace() + ":" + cacheName + ":" + key;
        }

        return cacheName + ":" + key;
    }

    /**
     * 根据 namespace 和 cacheName 生成前缀（用于批量清空）
     *
     * @param cacheName 缓存分组名称
     * @return 前缀字符串，如 "namespace:cacheName:" 或 "cacheName:" 或 ""
     */
    default String buildKeyPrefix(String cacheName) {
        if (StrUtil.isBlank(cacheName)) {
            throw new IllegalArgumentException("cacheName must not be null or blank");
        }
        if (!StrUtil.isBlank(getNamespace())) {
            return getNamespace() + ":" + cacheName + ":";
        }

        return cacheName + ":";
    }



    // ---------- 基本存取----------

    /**
     * 存入缓存，使用默认 TTL
     *
     * @param cacheName 缓存分组名称
     * @param key       缓存 key
     * @param value     缓存值
     */
    default void put(String cacheName, String key, Object value) {
        put(cacheName, key, value, getDefaultTtl());
    }

    /**
     * 存入缓存，指定 TTL（秒）
     *
     * @param cacheName 缓存分组名称
     * @param key       缓存 key
     * @param value     缓存值
     * @param ttlSeconds TTL 秒数
     */
    void put(String cacheName, String key, Object value, long ttlSeconds);

    /**
     * 获取缓存值
     *
     * @param cacheName 缓存分组名称
     * @param key       缓存 key
     * @return 缓存值，不存在返回 null
     */
    <T> T get(String cacheName, String key);

    // ---------- nullValue 支持 ----------

    /**
     * 是否允许缓存 null 值
     * <p>
     * 当返回 true 时，put(null) 会内部存储 NullValue 哨兵对象，
     * get() 识别哨兵后返回 null，exists() 可区分"键不存在"和"值为 null"。
     * 默认返回 false（不启用）。
     */
    default boolean isNullValue() {
        return false;
    }

    /**
     * 检查指定缓存键是否存在（区别于"值是否为 null"）
     * <p>
     * 当启用 nullValue 模式时，缓存中可能存在值为 null 的条目。
     * 该方法返回 true 表示键存在（即使值为 null），false 表示键确实不存在。
     *
     * @param cacheName 缓存分组名称
     * @param key       缓存 key
     * @return true 表示键存在，false 表示键不存在或参数无效
     */
    default boolean exists(String cacheName, String key) {
        if (StrUtil.isBlank(cacheName) || StrUtil.isBlank(key)) {
            return false;
        }
        return get(cacheName, key) != null;
    }

    /**
     * 缓存回源：如果缓存不存在，则调用 supplier 获取值并缓存
     * <p>
     * 当 isNullValue()=true 且键存在（值为 NullValue 哨兵）时直接返回 null，不穿透到 supplier。
     *
     * @param cacheName  缓存分组名称
     * @param key        缓存 key
     * @param supplier   值提供器
     * @param ttlSeconds TTL 秒数
     * @return 缓存值或新计算的值
     */
    default <T> T get(String cacheName, String key, Supplier<T> supplier, long ttlSeconds) {
        T value = get(cacheName, key);
        if (value != null) return value;
        if (isNullValue() && exists(cacheName, key)) {
            return null;
        }
        value = supplier.get();
        if (value != null || isNullValue()) {
            put(cacheName, key, value, ttlSeconds);
        }
        return value;
    }

    /**
     * 缓存回源，使用默认 TTL
     *
     * @param cacheName 缓存分组名称
     * @param key       缓存 key
     * @param supplier  值提供器
     * @return 缓存值或新计算的值
     */
    default <T> T get(String cacheName, String key, Supplier<T> supplier) {
        return get(cacheName, key, supplier, getDefaultTtl());
    }

    // ---------- 批量操作----------

    /**
     * 批量存入缓存
     *
     * @param cacheName  缓存分组名称
     * @param map        键值对
     * @param ttlSeconds TTL 秒数
     */
    default void putAll(String cacheName, Map<String, Object> map, long ttlSeconds) {
        map.forEach((k, v) -> put(cacheName, k, v, ttlSeconds));
    }

    /**
     * 批量获取缓存值
     *
     * @param cacheName 缓存分组名称
     * @param keys      缓存 key 集合
     * @return 存在的键值对
     */
    default Map<String, Object> getAll(String cacheName, Collection<String> keys) {
        Map<String, Object> result = new HashMap<>();
        for (String key : keys) {
            Object val = get(cacheName, key);
            if (val != null) result.put(key, val);
        }
        return result;
    }

    // ---------- 删除----------

    /**
     * 删除指定缓存分组下的缓存条目，支持可变参数
     *
     * @param cacheName 缓存分组名称
     * @param keys      要删除的 key（支持单参数、多参数）
     */
    void evict(String cacheName, String... keys);

    // ---------- 生命周期 ----------

    /**
     * 清空当前缓存实例下的所有数据（所有 cacheName），同时清空元数据
     */
    void clearAll();

    /**
     * 获取缓存名称（实例名，非分组名）
     */
    String getName();

    /**
     * 获取缓存类型
     */
    String getType();

    // ---------- 原生实例（逃生口）----------

    /**
     * 获取原生缓存实例
     * <p>
     * CaffeineCache 返回 com.github.benmanes.caffeine.cache.Cache
     * RedissonCache 返回 org.redisson.api.RMapCache
     * LocalCache 返回 java.util.concurrent.ConcurrentHashMap
     */
    <T> T getNativeCache();

    // ---------- 可选 TTL 操作 ----------

    /**
     * 设置缓存分组下某个 key 的过期时间（可选操作）
     *
     * @param cacheName 缓存分组名称
     * @param key       缓存 key
     * @param ttlSeconds TTL 秒数
     * @return 是否成功
     */
    default boolean setTtl(String cacheName, String key, long ttlSeconds) {
        throw new UnsupportedOperationException("setTtl not supported");
    }

    /**
     * 获取缓存分组下某个 key 的剩余 TTL（可选操作）
     *
     * @param cacheName 缓存分组名称
     * @param key       缓存 key
     * @return 剩余秒数，-1 表示永久，-2 表示不存在
     */
    default long getTtl(String cacheName, String key) {
        throw new UnsupportedOperationException("getTtl not supported");
    }

    /**
     * 获取默认 TTL
     */
    long getDefaultTtl();

    /**
     * 设置默认 TTL
     */
    void setDefaultTtl(long ttlSeconds);

    // ---------- 缓存分组元数据管理 ----------

    /**
     * 获取当前缓存实例中已记录的所有 cacheName 集合
     * <p>
     * 适应底层缓存特性，保证数据持久化：
     * <ul>
     *   <li>Redis/Redisson 实现：使用固定 key 维护 Set 结构</li>
     *   <li>本地缓存：内部维护线程安全的 Set</li>
     * </ul>
     *
     * @return 已记录的 cacheName 集合
     */
    Set<String> getCacheNames();

    /**
     * 清除指定 cacheName 下的所有缓存条目，并移除元数据
     *
     * @param cacheNames 要清除的缓存分组名称（可变参数）
     */
    void clear(String... cacheNames);

    // ---------- 命名空间 ----------

    /**
     * 获取当前缓存实例使用的命名空间
     *
     * @return namespace，未配置则返回空字符串
     */
    default String getNamespace() {
        return DEFAULT_NAMESPACE;
    }

}
