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

package cn.hg.aifei.cache.impl.distribute.redis;

import cn.aifei.util.Prop;
import cn.hg.aifei.cache.api.ICacheProvider;
import cn.hg.aifei.cache.api.ICache;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.redisson.config.SingleServerConfig;

/**
 * Redisson 缓存提供者
 * <p>
 * 根据配置创建 RedissonCache 实例，支持单机和集群模式。
 *
 * <p>配置参数：
 * <ul>
 *   <li>&lt;prefix&gt;address - Redis 地址，如 127.0.0.1:6379（默认 127.0.0.1:6379）</li>
 *   <li>&lt;prefix&gt;password - Redis 密码（可选）</li>
 *   <li>&lt;prefix&gt;database - 数据库编号（默认 0）</li>
 *   <li>&lt;prefix&gt;ttl - 默认 TTL 秒数（默认 3600）</li>
 *   <li>&lt;prefix&gt;redissonJson - Redisson JSON 配置路径（可选，如果指定则使用该配置）</li>
 * </ul>
 *
 * @author aifei
 */
public class RedissonCacheProvider implements ICacheProvider {

    private static final String TYPE = "redisson";

    /** 默认 Redis 地址 */
    private static final String DEFAULT_ADDRESS = "127.0.0.1:6379";

    /** 默认数据库编号 */
    private static final int DEFAULT_DATABASE = 0;

    /** 默认 TTL：1小时 */
    private static final long DEFAULT_TTL = 3600;

    /** RedissonClient 实例（如果由本 Provider 管理） */
    private RedissonClient managedRedisson;

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
        // 读取配置
        String address = prop.get(prefix + "address", DEFAULT_ADDRESS);
        String password = prop.get(prefix + "password");
        int database = prop.getInt(prefix + "database", DEFAULT_DATABASE);
        long ttl = prop.getLong(prefix + "ttl", DEFAULT_TTL);
        String redissonJson = prop.get(prefix + "redissonJson");

        // 获取或创建 RedissonClient
        RedissonClient redisson = createRedissonClient(prop, address, password, database, redissonJson);

        return new RedissonCache(redisson, name, ttl);
    }

    /**
     * 创建 RedissonClient
     */
    private RedissonClient createRedissonClient(Prop prop, String address,
                                                  String password, int database, String redissonJson) {
        // 如果配置了 redisson.json，使用文件配置
        if (redissonJson != null && !redissonJson.isEmpty()) {
            return Redisson.create();
        }

        // 否则使用程序化配置
        Config config = new Config();

        SingleServerConfig serverConfig = config.useSingleServer()
                .setAddress("redis://" + address)
                .setDatabase(database)
                .setConnectionPoolSize(64)
                .setConnectionMinimumIdleSize(10);

        if (password != null && !password.isEmpty()) {
            serverConfig.setPassword(password);
        }

        managedRedisson = Redisson.create(config);
        return managedRedisson;
    }

    /**
     * 获取管理的 RedissonClient（如果有）
     */
    public RedissonClient getManagedRedisson() {
        return managedRedisson;
    }

    /**
     * 关闭管理的 RedissonClient
     */
    public void shutdown() {
        if (managedRedisson != null && !managedRedisson.isShutdown()) {
            managedRedisson.shutdown();
        }
    }
}
