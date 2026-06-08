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
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package cn.hg.aifei.cache.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 获取及更新缓存注解 - 方法执行前已缓存则直接返回，否则执行方法后将返回值写入缓存。
 *
 * <pre>
 * 使用示例：
 *   &#64;CachePut(name = "product", key = "prod-#para(result.id)", ttlSeconds = 300)
 *   public Product updateProduct(Product product) { ... }
 * </pre>
 *
 * @author aifei
 */
@Inherited
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD})
public @interface CachePut {

    /**
     * 缓存名称（cacheName），用于缓存分组隔离
     */
    String name();

    /**
     * 缓存 Key，支持 #para() / #header() / #path() 表达式
     */
    String key();

    /**
     * 缓存有效期（秒），使用框架默认 TTL
     */
    int ttlSeconds() default 0;

    /**
     * 是否缓存 null 值，默认 false
     */
    boolean nullable() default false;

    /**
     * 指定缓存存储实例，为空则使用默认缓存实例
     */
    String cache() default "";
}
