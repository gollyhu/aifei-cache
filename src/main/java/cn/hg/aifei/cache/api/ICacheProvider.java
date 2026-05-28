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

import cn.aifei.util.Prop;

/**
 * 缓存提供者接口
 * <p>
 * 负责根据配置创建 ICache 实例。
 * 每个缓存实现（Caffeine、Redisson、Local）都有对应的 Provider。
 *
 * @author aifei
 */
public interface ICacheProvider {

    /**
     * 获取缓存类型标识
     *
     * @return 类型标识，如 "caffeine"、"redisson"、"local"
     */
    String getType();

    /**
     * 构建缓存实例
     *
     * @param name 缓存名称
     * @param type 缓存类型
     * @param prop 配置属性
     * @return 缓存实例
     */
    ICache buildCache(String name, String type, Prop prop);

    /**
     * 构建缓存实例（带配置前缀）
     * <p>
     * 主缓存使用 cache. 前缀，扩展缓存使用 cache.&lt;name&gt;. 前缀
     *
     * @param name 缓存名称
     * @param type 缓存类型
     * @param prop 配置属性
     * @param prefix 配置前缀（如 "cache." 或 "cache.xxx."）
     * @return 缓存实例
     */
    ICache buildCache(String name, String type, Prop prop, String prefix);

    /**
     * 关闭 Provider 管理的资源（如连接池、客户端等）
     * <p>
     * 默认空实现，子类可覆盖以实现资源释放逻辑。
     */
    default void shutdown() {
    }
}
