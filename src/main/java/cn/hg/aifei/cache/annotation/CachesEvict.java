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
 * 组合缓存清除注解 - 允许在一个方法上配置多条 @CacheEvict 规则。
 *
 * <pre>
 * 使用示例：
 *   &#64;CachesEvict({
 *       &#64;CacheEvict(name = "product", key = "prod-#para(id)"),
 *       &#64;CacheEvict(name = "productList", key = "list-#para(categoryId)")
 *   })
 *   public void deleteProductAndEvictList(Long id, Long categoryId) { ... }
 * </pre>
 *
 * @author aifei
 */
@Inherited
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD})
public @interface CachesEvict {

    /**
     * 多个缓存删除规则集合
     */
    CacheEvict[] value();
}
