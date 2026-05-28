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
 * 清除缓存注解 - 用于删除指定缓存 Key 或清空整个缓存分组。
 *
 * <pre>
 * 使用示例：
 *   // 清除指定 Key
 *   &#64;CacheEvict(name = "product", key = {"prod-#para(id)"})
 *   public void deleteProduct(Long id) { ... }
 *
 *   // 清除整个缓存区域
 *   &#64;CacheEvict(name = "product")
 *   public void clearAllProducts() { ... }
 * </pre>
 *
 * @author aifei
 */
@Inherited
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD})
public @interface CacheEvict {

    /**
     * 缓存名称（cacheName），用于缓存分组隔离
     */
    String name();

    /**
     * 一个或多个缓存 Key，不设置则清除该缓存分组下所有条目
     */
    String[] key() default {};

    /**
     * 是否对 null 值进行缓存，默认为 false
     */
    boolean nullable() default false;

    /**
     * 指定缓存存储实例，为空则使用默认缓存实例
     */
    String cache() default "";
}
