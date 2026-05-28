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
import cn.hg.aifei.cache.api.ICache;
import cn.hg.aifei.cache.api.ICacheProvider;
import org.apache.commons.pool2.impl.GenericObjectPoolConfig;
import redis.clients.jedis.Connection;
import redis.clients.jedis.ConnectionPoolConfig;
import redis.clients.jedis.DefaultJedisClientConfig;
import redis.clients.jedis.RedisClient;
import cn.hg.aifei.cache.serializer.Fastjson2ICacheSerializer;

/**
 * Jedis 缓存提供者
 * <p>
 * 根据配置创建 JedisCache 实例，管理 RedisClient 生命周期。
 *
 * <p>配置参数：
 * <ul>
 *   <li>&lt;prefix&gt;address - Redis 地址，如 127.0.0.1:6379（默认 127.0.0.1:6379）</li>
 *   <li>&lt;prefix&gt;password - Redis 密码（可选）</li>
 *   <li>&lt;prefix&gt;database - 数据库编号（默认 0）</li>
 *   <li>&lt;prefix&gt;ttl - 默认 TTL 秒数（默认 3600）</li>
 *   <li>&lt;prefix&gt;maxTotal - 连接池最大连接数（默认 8）</li>
 *   <li>&lt;prefix&gt;maxIdle - 连接池最大空闲连接数（默认 8）</li>
 *   <li>&lt;prefix&gt;minIdle - 连接池最小空闲连接数（默认 0）</li>
 * </ul>
 *
 * @author aifei
 */
public class JedisCacheProvider implements ICacheProvider {

    private static final String TYPE = "jedis";

    /** 默认 Redis 地址 */
    private static final String DEFAULT_ADDRESS = "127.0.0.1:6379";

    /** 默认数据库编号 */
    private static final int DEFAULT_DATABASE = 0;

    /** 默认 TTL：1小时 */
    private static final long DEFAULT_TTL = 3600;

    /** 默认最大连接数 */
    private static final int DEFAULT_MAX_TOTAL = 8;

    /** 默认最大空闲连接数 */
    private static final int DEFAULT_MAX_IDLE = 8;

    /** 默认最小空闲连接数 */
    private static final int DEFAULT_MIN_IDLE = 0;

    /** 本 Provider 管理的 RedisClient */
    private RedisClient managedClient;

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
        int maxTotal = prop.getInt(prefix + "maxTotal", DEFAULT_MAX_TOTAL);
        int maxIdle = prop.getInt(prefix + "maxIdle", DEFAULT_MAX_IDLE);
        int minIdle = prop.getInt(prefix + "minIdle", DEFAULT_MIN_IDLE);

        // 解析 host:port
        String[] parts = address.split(":");
        String host = parts[0].trim();
        int port = parts.length > 1 ? Integer.parseInt(parts[1].trim()) : 6379;

        // 创建连接池配置
        GenericObjectPoolConfig<Connection> poolConfig = new ConnectionPoolConfig();
        poolConfig.setMaxTotal(maxTotal);
        poolConfig.setMaxIdle(maxIdle);
        poolConfig.setMinIdle(minIdle);

        // 创建客户端配置
        DefaultJedisClientConfig.Builder clientConfigBuilder = DefaultJedisClientConfig.builder()
                .timeoutMillis(2000)
                .database(database);
        if (password != null && !password.isEmpty()) {
            clientConfigBuilder.password(password);
        }

        // 创建 RedisClient
        managedClient = RedisClient.builder()
                .hostAndPort(host, port)
                .clientConfig(clientConfigBuilder.build())
                .poolConfig(poolConfig)
                .build();

        return new JedisCache(managedClient, name, ttl, new Fastjson2ICacheSerializer());
    }

    /**
     * 获取管理的 RedisClient
     */
    public RedisClient getManagedRedisClient() {
        return managedClient;
    }

    /**
     * 关闭 RedisClient（释放连接池）
     */
    public void shutdown() {
        if (managedClient != null) {
            managedClient.close();
        }
    }
}
