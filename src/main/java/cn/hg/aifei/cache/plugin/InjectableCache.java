package cn.hg.aifei.cache.plugin;

import cn.hg.aifei.cache.api.ICache;
import cn.hg.aifei.cache.core.CacheUtil;

import java.util.Set;

final class InjectableCache implements ICache{

    static final InjectableCache INSTANCE = new InjectableCache();

    private InjectableCache() {}

    public void put(String cacheName, String key, Object value, long ttlSeconds) {
        CacheUtil.put(cacheName, key, value, ttlSeconds);
    }

    public <T> T get(String cacheName, String key) {
        return CacheUtil.get(cacheName, key);
    }

    public void evict(String cacheName, String... keys) {
        CacheUtil.evict(cacheName, keys);
    }

    public void clearAll() {
        CacheUtil.clearAll();
    }

    public String getName() {
        return CacheUtil.getName();
    }

    public String getType() {
        return CacheUtil.getType();
    }

    public <T> T getNativeCache() {
        return CacheUtil.getNativeCache();
    }

    public long getDefaultTtl() {
        return CacheUtil.getDefaultTtl();
    }

    public void setDefaultTtl(long ttlSeconds) {
        CacheUtil.setDefaultTtl(ttlSeconds);
    }

    public Set<String> getCacheNames() {
        return CacheUtil.getCacheNames();
    }

    public void clear(String... cacheNames) {
        CacheUtil.clear(cacheNames);
    }
}
