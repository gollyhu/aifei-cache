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

package cn.hg.aifei.cache.api;

import java.util.Set;
import java.util.function.Supplier;
import cn.aifei.util.StrUtil;

/**
 * 缓存抽象基类
 * <p>
 * 提供 name、defaultTtl、namespace 等公共字段的统一管理，
 * 以及 put/get/evict/clear 的模板方法（含 key/value 校验及存储键生成）。
 * 子类只需实现具体的存储逻辑。
 *
 * <h3>存储键生成规则</h3>
 * <ul>
 *   <li>namespace 非空 + cacheName 非空：{@code namespace:cacheName:key}</li>
 *   <li>namespace 非空 + cacheName 为空：{@code namespace:key}</li>
 *   <li>namespace 为空 + cacheName 非空：{@code cacheName:key}</li>
 *   <li>namespace 为空 + cacheName 为空：{@code key}</li>
 * </ul>
 *
 * @author aifei
 */
public abstract class AbstractCache implements ICache {

    /**
     * 缓存名称
     */
    private final String name;

    /**
     * 默认 TTL 秒数
     */
    protected volatile long defaultTtl;

    /**
     * 命名空间
     */
    protected String namespace = ICache.DEFAULT_NAMESPACE;

    /**
     * 是否允许缓存 null 值（默认 false）
     * <p>
     * 当为 true 时，put(null) 会内部存储 NullValue 哨兵对象，
     * get() 识别哨兵后返回 null，isKeyPresent() 可区分"键不存在"和"值为 null"。
     */
    protected volatile boolean nullValue = false;

    /**
     * 构造函数
     *
     * @param name       缓存名称
     * @param defaultTtl 默认 TTL 秒数
     */
    protected AbstractCache(String name, long defaultTtl) {
        this.name = name;
        this.defaultTtl = defaultTtl;
    }

    // ==================== 元数据 ====================

    @Override
    public String getName() {
        return name;
    }

    @Override
    public long getDefaultTtl() {
        return defaultTtl;
    }

    @Override
    public void setDefaultTtl(long ttlSeconds) {
        this.defaultTtl = ttlSeconds;
    }

    /**
     * 设置命名空间
     */
    public void setNamespace(String namespace) {
        if (namespace == null) {
            this.namespace = "";
        } else {
            this.namespace = namespace.trim();
        }
    }

    /**
     * 设置是否允许缓存 null 值
     */
    public void setNullValue(boolean nullValue) {
        this.nullValue = nullValue;
    }

    /**
     * 获取是否允许缓存 null 值
     */
    public boolean isNullValue() {
        return nullValue;
    }

    @Override
    public String getNamespace() {
        return namespace;
    }

    // ==================== 缓存分组元数据 key ====================

    /**
     * 获取用于存储 cacheName 元数据的 Redis key（仅分布式缓存使用）
     */
    protected String getCacheNamesMetaKey() {
        if (namespace != null && !namespace.isEmpty()) {
            return namespace + ":__cacheNames__";
        }
        return "__cacheNames__";
    }

    // ==================== 校验工具方法 ====================

    /**
     * 校验 key 是否无效（null 或 空白字符串）
     *
     * @return true 表示无效
     */
    protected boolean isInvalidKey(String key) {
        return key == null || key.trim().isEmpty();
    }

    // ==================== 模板方法 ====================

    @Override
    public void put(String cacheName, String key, Object value, long ttlSeconds) {
        if (isInvalidKey(key)) {
            return;
        }
        // nullValue=true 时，将 null 替换为 NullValue 哨兵
        if (value == null) {
            if (!nullValue) {
                return;
            }
            value = NullValue.INSTANCE;
        }
        String storageKey = buildStorageKey(cacheName, key);
        doPut(storageKey, value, ttlSeconds);
        // 记录 cacheName 元数据
        onPutCacheName(cacheName);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T get(String cacheName, String key) {
        if (isInvalidKey(key)) {
            return null;
        }
        T value = doGet(buildStorageKey(cacheName, key));
        // NullValue 哨兵 → 对外返回 null
        if (value instanceof NullValue) {
            return null;
        }
        return value;
    }

    /**
     * 检查键在缓存中是否存在（区别于"值是否为 null"）
     * <p>
     * 当 nullValue=true 时，用来区分"键不存在"（返回 false）和"键存在但值为 null"（返回 true）。
     */
    protected boolean isKeyPresent(String cacheName, String key) {
        if (isInvalidKey(key)) {
            return false;
        }
        Object value = doGet(buildStorageKey(cacheName, key));
        // NullValue 哨兵表示键存在
        return value != null;
    }

    @Override
    public boolean exists(String cacheName, String key) {
        if (StrUtil.isBlank(cacheName) || isInvalidKey(key)) {
            return false;
        }
        return doGet(buildStorageKey(cacheName, key)) != null;
    }

    /**
     * 缓存回源：如果缓存不存在，则调用 supplier 获取值并缓存
     * <p>
     * 当 nullValue=true 时，若键存在（值为 NullValue 哨兵）则直接返回 null，不穿透到 supplier。
     */
    @Override
    public <T> T get(String cacheName, String key, Supplier<T> supplier, long ttlSeconds) {
        T value = get(cacheName, key);
        if (value != null) {
            return value;
        }
        // nullValue=true 且键存在（值为 NullValue）：直接返回 null，不穿透
        if (nullValue && isKeyPresent(cacheName, key)) {
            return null;
        }
        // 键不存在，调 supplier 回源
        value = supplier.get();
        if (value != null || nullValue) {
            put(cacheName, key, value, ttlSeconds);
        }
        return value;
    }

    @Override
    public void evict(String cacheName, String... keys) {
        if (keys == null) {
            return;
        }
        for (String key : keys) {
            if (key != null && !key.trim().isEmpty()) {
                doEvict(buildStorageKey(cacheName, key));
            }
        }
    }

    @Override
    public void clearAll() {
        doClear();
        // 清空 cacheName 元数据
        doClearCacheNamesMeta();
    }

    // ==================== cacheName 元数据钩子（子类可按需覆盖） ====================

    /**
     * 当执行 put 操作时记录 cacheName（子类覆盖以持久化）
     *
     * @param cacheName 缓存分组名称
     */
    protected void onPutCacheName(String cacheName) {
        // 默认空实现，子类覆盖
    }

    /**
     * 清空所有 cacheName 元数据
     */
    protected void doClearCacheNamesMeta() {
        // 默认空实现，子类覆盖
    }

    // ==================== 子类实现的抽象方法（接收存储键） ====================

    /**
     * 执行实际的 put 存储逻辑（调用前已通过 key/value 校验，key 已为存储键）
     */
    protected abstract void doPut(String key, Object value, long ttlSeconds);

    /**
     * 执行实际的 get 查询逻辑（调用前已通过 key 校验，key 已为存储键）
     */
    protected abstract <T> T doGet(String key);

    /**
     * 执行单个 key 的删除逻辑（调用前已通过 key 校验，key 已为存储键）
     */
    protected abstract void doEvict(String key);

    /**
     * 执行清空所有缓存的逻辑
     */
    protected abstract void doClear();

    // ==================== 缓存分组清空相关抽象方法 ====================

    /**
     * 获取当前缓存实例中所有已记录的 cacheName 集合
     */
    @Override
    public abstract Set<String> getCacheNames();

    /**
     * 清除指定 cacheName 下的所有缓存条目
     */
    @Override
    public abstract void clear(String... cacheNames);
}
