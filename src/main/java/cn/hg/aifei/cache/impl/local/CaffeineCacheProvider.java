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

/**
 * Caffeine 缓存提供者
 * <p>
 * 根据配置创建 CaffeineCache 实例。
 *
 * <p>配置参数：
 * <ul>
 *   <li>&lt;prefix&gt;maxSize - 最大容量（默认 10000）</li>
 *   <li>&lt;prefix&gt;ttl - 默认 TTL 秒数（默认 3600）</li>
 * </ul>
 *
 * @author aifei
 */
public class CaffeineCacheProvider implements ICacheProvider {

    private static final String TYPE = "caffeine";

    /** 默认最大容量 */
    private static final int DEFAULT_MAX_SIZE = 10000;

    /** 默认 TTL：1小时 */
    private static final long DEFAULT_TTL = 3600;

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
        // 从配置中读取参数
        int maxSize = prop.getInt(prefix + "maxSize", DEFAULT_MAX_SIZE);
        long ttl = prop.getLong(prefix + "ttl", DEFAULT_TTL);

        // 创建缓存实例
        return new CaffeineCache(name, maxSize, ttl);
    }
}
