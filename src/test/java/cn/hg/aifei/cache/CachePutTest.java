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

package cn.hg.aifei.cache;

import cn.hg.aifei.cache.annotation.CachePut;
import org.junit.Test;

import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Method;

import static org.junit.Assert.*;

/**
 * CachePut 注解元数据及默认值验证测试
 */
public class CachePutTest {

    // ─── 测试载体方法 ───

    @CachePut(name = "test", key = "test-#para(id)")
    public void annotatedWithDefaults() {
    }

    @CachePut(name = "product", key = "prod-#para(id)", ttlSeconds = 300, nullable = true, cache = "redisCache")
    public void annotatedWithCustomValues() {
    }

    // ─── 正常场景 ───

    @Test
    public void testDefaultValues_WhenOnlyRequiredSet() throws NoSuchMethodException {
        Method method = CachePutTest.class.getDeclaredMethod("annotatedWithDefaults");
        CachePut annotation = method.getAnnotation(CachePut.class);

        // Given: @CachePut(name="test", key="test-#para(id)"), 其他使用默认值
        // Then: 默认值符合规范
        assertEquals("ttlSeconds default should be 0", 0, annotation.ttlSeconds());
        assertFalse("nullable default should be false", annotation.nullable());
        assertEquals("cache default should be empty string", "", annotation.cache());
    }

    @Test
    public void testRequired_name_CanBeSet() throws NoSuchMethodException {
        Method method = CachePutTest.class.getDeclaredMethod("annotatedWithDefaults");
        CachePut annotation = method.getAnnotation(CachePut.class);

        assertEquals("name should be 'test'", "test", annotation.name());
    }

    @Test
    public void testRequired_key_CanBeSet() throws NoSuchMethodException {
        Method method = CachePutTest.class.getDeclaredMethod("annotatedWithDefaults");
        CachePut annotation = method.getAnnotation(CachePut.class);

        assertEquals("key should be set", "test-#para(id)", annotation.key());
    }

    @Test
    public void testCustomValues_AllAttributesSet() throws NoSuchMethodException {
        Method method = CachePutTest.class.getDeclaredMethod("annotatedWithCustomValues");
        CachePut annotation = method.getAnnotation(CachePut.class);

        // Given: @CachePut(name="product", key="prod-#para(id)", ttlSeconds=300, nullable=true, cache="redisCache")
        // Then: 所有属性值正确读取
        assertEquals("name mismatch", "product", annotation.name());
        assertEquals("key mismatch", "prod-#para(id)", annotation.key());
        assertEquals("ttlSeconds mismatch", 300, annotation.ttlSeconds());
        assertTrue("nullable should be true", annotation.nullable());
        assertEquals("cache mismatch", "redisCache", annotation.cache());
    }

    @Test
    public void testRetention_IsRuntime() {
        // Given: CachePut 注解定义
        Retention retention = CachePut.class.getAnnotation(Retention.class);

        // Then: 必须是 RUNTIME 保留策略，框架运行时需要反射读取
        assertNotNull("@Retention must be present", retention);
        assertEquals("Retention must be RUNTIME", RetentionPolicy.RUNTIME, retention.value());
    }

    @Test
    public void testTarget_IsMethod() {
        // Given: CachePut 注解定义
        Target target = CachePut.class.getAnnotation(Target.class);

        // Then: 只能用于方法上
        assertNotNull("@Target must be present", target);
        ElementType[] elementTypes = target.value();
        assertEquals("Target should only contain METHOD", 1, elementTypes.length);
        assertEquals("Target should be METHOD", ElementType.METHOD, elementTypes[0]);
    }

    @Test
    public void testIsInherited() {
        // Given: CachePut 注解定义
        Inherited inherited = CachePut.class.getAnnotation(Inherited.class);

        // Then: 标记为 @Inherited，子类继承父类方法时注解可传递
        assertNotNull("CachePut must be @Inherited", inherited);
    }

    // ─── 边界场景 ───

    @Test
    public void testTtlSeconds_Zero_MeansUseDefaultTtl() throws NoSuchMethodException {
        Method method = CachePutTest.class.getDeclaredMethod("annotatedWithDefaults");
        CachePut annotation = method.getAnnotation(CachePut.class);

        // Given: ttlSeconds 使用默认值 0
        // Then: ttlSeconds=0 表示使用框架配置的默认 TTL
        assertEquals("ttlSeconds=0 means use framework default TTL", 0, annotation.ttlSeconds());
    }

    @Test
    public void testCache_EmptyString_MeansUseDefaultCacheInstance() throws NoSuchMethodException {
        Method method = CachePutTest.class.getDeclaredMethod("annotatedWithDefaults");
        CachePut annotation = method.getAnnotation(CachePut.class);

        // Given: cache 使用默认值 ""
        // Then: cache="" 表示使用默认缓存实例 (CacheManager.getCache(DEFAULT))
        assertEquals("cache='' means use default cache instance", "", annotation.cache());
    }

    @Test
    public void testAnnotationTypeName_MatchesExpected() {
        // Then: 全限定名必须匹配，框架通过类名查找注解
        assertEquals("CachePut class name",
                "cn.hg.aifei.cache.annotation.CachePut",
                CachePut.class.getName());
    }
}
