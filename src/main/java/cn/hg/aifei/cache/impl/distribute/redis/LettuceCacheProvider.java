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

package cn.hg.aifei.cache.impl.distribute.redis;

import cn.aifei.util.Prop;
import cn.hg.aifei.cache.api.ICacheProvider;
import cn.hg.aifei.cache.api.ICache;
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;

/**
 * Lettuce 缓存提供者
 * <p>
 * 根据配置创建 LettuceCache 实例，管理 RedisClient 生命周期。
 *
 * <p>配置参数：
 * <ul>
 *   <li>&lt;prefix&gt;address - Redis 地址，如 127.0.0.1:6379（默认 127.0.0.1:6379）</li>
 *   <li>&lt;prefix&gt;password - Redis 密码（可选）</li>
 *   <li>&lt;prefix&gt;database - 数据库编号（默认 0）</li>
 *   <li>&lt;prefix&gt;ttl - 默认 TTL 秒数（默认 3600）</li>
 * </ul>
 *
 * @author aifei
 */
public class LettuceCacheProvider implements ICacheProvider {

    private static final String TYPE = "lettuce";

    /** 默认 Redis 地址 */
    private static final String DEFAULT_ADDRESS = "127.0.0.1:6379";

    /** 默认数据库编号 */
    private static final int DEFAULT_DATABASE = 0;

    /** 默认 TTL：1小时 */
    private static final long DEFAULT_TTL = 3600;

    /** 本 Provider 管理的 RedisClient */
    private RedisClient managedRedisClient;

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
        String address = prop.get(prefix + "address", DEFAULT_ADDRESS);
        String password = prop.get(prefix + "password");
        int database = prop.getInt(prefix + "database", DEFAULT_DATABASE);
        long ttl = prop.getLong(prefix + "ttl", DEFAULT_TTL);

        // 解析 host:port
        String[] parts = address.split(":");
        String host = parts[0].trim();
        int port = parts.length > 1 ? Integer.parseInt(parts[1].trim()) : 6379;

        // 构建 RedisURI
        RedisURI.Builder uriBuilder = RedisURI.builder()
                .withHost(host)
                .withPort(port)
                .withDatabase(database);

        if (password != null && !password.isEmpty()) {
            uriBuilder.withPassword(password.toCharArray());
        }

        RedisURI redisURI = uriBuilder.build();
        managedRedisClient = RedisClient.create(redisURI);

        return new LettuceCache(managedRedisClient, name, ttl);
    }

    /**
     * 获取管理的 RedisClient
     */
    public RedisClient getManagedRedisClient() {
        return managedRedisClient;
    }

    /**
     * 关闭 RedisClient
     */
    public void shutdown() {
        if (managedRedisClient != null) {
            managedRedisClient.shutdown();
        }
    }
}
