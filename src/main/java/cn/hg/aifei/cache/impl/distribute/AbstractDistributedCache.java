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

package cn.hg.aifei.cache.impl.distribute;

import cn.hg.aifei.cache.api.AbstractCache;
import cn.hg.aifei.cache.api.CacheOperationException;

import java.util.function.Supplier;

/**
 * 分布式缓存抽象基类
 * <p>
 * 在 AbstractCache 的基础上，为所有分布式缓存添加统一的 try-catch 异常包装。
 * 子类只需实现 doPutSafely/doGetSafely/doEvictSafely/doClearSafely 方法，
 * 无需自己处理异常。
 * <p>
 * 注意：doPut/doGet/doEvict 中的 key 参数是 AbstractCache 已构建好的存储键
 * （如 namespace:cacheName:key）。
 *
 * @author aifei
 */
public abstract class AbstractDistributedCache extends AbstractCache {

    protected AbstractDistributedCache(String name, long defaultTtl) {
        super(name, defaultTtl);
    }

    // ==================== doXxx → doXxxSafely（带异常包装） ====================

    /**
     * 获取用于错误消息的缓存类型名称，默认返回 "distributed cache"
     * <p>
     * 子类可覆盖此方法指定更精确的类型名称（如 "redis"）。
     */
    protected abstract String getCacheTypeForError();

    @Override
    protected final void doPut(String key, Object value, long ttlSeconds) {
        String msg = "Failed to put to " + getCacheTypeForError() + ": " + key;
        wrapExceptionVoid(msg, () -> doPutSafely(key, value, ttlSeconds));
    }

    @Override
    @SuppressWarnings("unchecked")
    protected final <T> T doGet(String key) {
        String msg = "Failed to get from " + getCacheTypeForError() + ": " + key;
        return wrapException(msg, () -> doGetSafely(key));
    }

    @Override
    protected final void doEvict(String key) {
        String msg = "Failed to evict from " + getCacheTypeForError() + ": " + key;
        wrapExceptionVoid(msg, () -> doEvictSafely(key));
    }

    @Override
    protected final void doClear() {
        String msg = "Failed to clear " + getCacheTypeForError() + " cache: " + getName();
        wrapExceptionVoid(msg, this::doClearSafely);
    }

    // ==================== 异常处理工具方法 ====================

    /**
     * 包装带返回值的操作，将受检异常转为 CacheOperationException
     */
    @SuppressWarnings("unchecked")
    protected <T> T wrapException(String message, ExceptionSupplier<?> supplier) {
        try {
            return (T) supplier.get();
        } catch (Exception e) {
            throw new CacheOperationException(message, e);
        }
    }

    /**
     * 包装无返回值的操作，将受检异常转为 CacheOperationException
     */
    protected void wrapExceptionVoid(String message, VoidRunnable runnable) {
        try {
            runnable.run();
        } catch (Exception e) {
            throw new CacheOperationException(message, e);
        }
    }

    // ==================== 缓存回源保护（分布式锁防击穿） ====================

    /**
     * 尝试获取分布式锁
     *
     * @param lockKey        锁键
     * @param timeoutSeconds 锁超时秒数
     * @return 是否获取成功
     */
    protected abstract boolean tryLock(String lockKey, long timeoutSeconds);

    /**
     * 释放分布式锁
     *
     * @param lockKey 锁键
     */
    protected abstract void unlock(String lockKey);

    /**
     * 缓存回源（带分布式锁防击穿保护）
     * <p>
     * 当多个并发请求同时发现缓存失效时，只有获取到分布式锁的线程执行回源操作，
     * 其他线程等待后重新从缓存获取，避免缓存击穿和雪崩。
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
        if (isNullValue() && isKeyPresent(cacheName, key)) {
            return null;
        }

        String lockKey = buildStorageKey(cacheName, key) + ":lock";
        if (tryLock(lockKey, 10)) {
            try {
                // Double-check：获取锁后再次检查缓存
                value = get(cacheName, key);
                if (value != null) {
                    return value;
                }
                if (isNullValue() && isKeyPresent(cacheName, key)) {
                    return null;
                }
                value = supplier.get();
                if (value != null || isNullValue()) {
                    put(cacheName, key, value, ttlSeconds);
                }
                return value;
            } finally {
                unlock(lockKey);
            }
        } else {
            // 获取锁失败，短暂等待后递归重试（最多重试 3 次）
            return retryGet(cacheName, key, supplier, ttlSeconds, 3);
        }
    }

    /**
     * 获取锁失败后的限定次数重试
     */
    private <T> T retryGet(String cacheName, String key, Supplier<T> supplier, long ttlSeconds, int retriesLeft) {
        if (retriesLeft <= 0) {
            // 重试耗尽，直接回源
            T value = supplier.get();
            if (value != null || isNullValue()) {
                put(cacheName, key, value, ttlSeconds);
            }
            return value;
        }
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        T value = get(cacheName, key);
        if (value != null) {
            return value;
        }
        if (isNullValue() && isKeyPresent(cacheName, key)) {
            return null;
        }
        return retryGet(cacheName, key, supplier, ttlSeconds, retriesLeft - 1);
    }

    // ==================== 子类实现的抽象方法（不加异常处理） ====================

    /**
     * 执行实际的分布式 put 存储逻辑
     */
    protected abstract void doPutSafely(String key, Object value, long ttlSeconds) throws Exception;

    /**
     * 执行实际的分布式 get 查询逻辑
     */
    protected abstract <T> T doGetSafely(String key) throws Exception;

    /**
     * 执行实际的单个 key 分布式删除逻辑
     */
    protected abstract void doEvictSafely(String key) throws Exception;

    /**
     * 执行实际的分布式清空逻辑
     */
    protected abstract void doClearSafely() throws Exception;

    // ==================== 函数式接口 ====================

    /**
     * 带返回值的操作（可抛出受检异常）
     */
    @FunctionalInterface
    protected interface ExceptionSupplier<T> {
        T get() throws Exception;
    }

    /**
     * 无返回值的操作（可抛出受检异常）
     */
    @FunctionalInterface
    protected interface VoidRunnable {
        void run() throws Exception;
    }

}
