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

import cn.hg.aifei.cache.annotation.CacheEvict;
import org.junit.Test;

import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Method;

import static org.junit.Assert.*;

/**
 * CacheEvict 注解元数据及默认值验证测试
 */
public class CacheEvictTest {

    // ─── 测试载体方法 ───

    @CacheEvict(name = "product", key = "prod-#para(id)")
    public void evictSingleKey() {
    }

    @CacheEvict(name = "product", key = {"prod-#para(id)", "list-#para(catId)"})
    public void evictMultipleKeys() {
    }

    @CacheEvict(name = "product")
    public void evictAllKeys() {
    }

    @CacheEvict(name = "user", key = {}, nullable = true, cache = "redisCache")
    public void evictWithCustomValues() {
    }

    // ─── 正常场景 ───

    @Test
    public void testDefaultValues_key_IsEmptyArray() throws NoSuchMethodException {
        Method method = CacheEvictTest.class.getDeclaredMethod("evictAllKeys");
        CacheEvict annotation = method.getAnnotation(CacheEvict.class);

        // Given: @CacheEvict(name="product")，不设置 key
        // Then: key 默认为空数组，表示清空整个 cacheName 分组
        assertNotNull("key array should not be null", annotation.key());
        assertEquals("default key should be empty array", 0, annotation.key().length);
    }

    @Test
    public void testDefaultValues_nullable_IsFalse() throws NoSuchMethodException {
        Method method = CacheEvictTest.class.getDeclaredMethod("evictAllKeys");
        CacheEvict annotation = method.getAnnotation(CacheEvict.class);

        assertFalse("nullable default should be false", annotation.nullable());
    }

    @Test
    public void testDefaultValues_cache_IsEmptyString() throws NoSuchMethodException {
        Method method = CacheEvictTest.class.getDeclaredMethod("evictAllKeys");
        CacheEvict annotation = method.getAnnotation(CacheEvict.class);

        assertEquals("cache default should be empty string", "", annotation.cache());
    }

    @Test
    public void testRequired_name_CanBeSet() throws NoSuchMethodException {
        Method method = CacheEvictTest.class.getDeclaredMethod("evictAllKeys");
        CacheEvict annotation = method.getAnnotation(CacheEvict.class);

        assertEquals("name should be 'product'", "product", annotation.name());
    }

    @Test
    public void testSingleKey_CanBeSet() throws NoSuchMethodException {
        Method method = CacheEvictTest.class.getDeclaredMethod("evictSingleKey");
        CacheEvict annotation = method.getAnnotation(CacheEvict.class);

        // Given: @CacheEvict(name="product", key="prod-#para(id)")
        // Then: key 数组包含单个元素
        assertEquals("key array should have 1 element", 1, annotation.key().length);
        assertEquals("key value mismatch", "prod-#para(id)", annotation.key()[0]);
    }

    @Test
    public void testMultipleKeys_CanBeSet() throws NoSuchMethodException {
        Method method = CacheEvictTest.class.getDeclaredMethod("evictMultipleKeys");
        CacheEvict annotation = method.getAnnotation(CacheEvict.class);

        // Given: @CacheEvict(name="product", key={"prod-#para(id)", "list-#para(catId)"})
        // Then: key 数组包含两个元素
        assertEquals("key array should have 2 elements", 2, annotation.key().length);
        assertEquals("first key mismatch", "prod-#para(id)", annotation.key()[0]);
        assertEquals("second key mismatch", "list-#para(catId)", annotation.key()[1]);
    }

    @Test
    public void testCustomValues_AllAttributesSet() throws NoSuchMethodException {
        Method method = CacheEvictTest.class.getDeclaredMethod("evictWithCustomValues");
        CacheEvict annotation = method.getAnnotation(CacheEvict.class);

        // Given: @CacheEvict(name="user", key={}, nullable=true, cache="redisCache")
        // Then: 所有属性值正确
        assertEquals("name mismatch", "user", annotation.name());
        assertEquals("key should be empty array", 0, annotation.key().length);
        assertTrue("nullable should be true", annotation.nullable());
        assertEquals("cache mismatch", "redisCache", annotation.cache());
    }

    @Test
    public void testRetention_IsRuntime() {
        // Given: CacheEvict 注解定义
        Retention retention = CacheEvict.class.getAnnotation(Retention.class);

        // Then: 必须是 RUNTIME，CacheInterceptor 运行时通过反射读取
        assertNotNull("@Retention must be present", retention);
        assertEquals("Retention must be RUNTIME", RetentionPolicy.RUNTIME, retention.value());
    }

    @Test
    public void testTarget_IsMethod() {
        // Given: CacheEvict 注解定义
        Target target = CacheEvict.class.getAnnotation(Target.class);

        // Then: 只能用于方法上
        assertNotNull("@Target must be present", target);
        ElementType[] elementTypes = target.value();
        assertEquals("Target should only contain METHOD", 1, elementTypes.length);
        assertEquals("Target should be METHOD", ElementType.METHOD, elementTypes[0]);
    }

    @Test
    public void testIsInherited() {
        // Given: CacheEvict 注解定义
        Inherited inherited = CacheEvict.class.getAnnotation(Inherited.class);

        // Then: 标记为 @Inherited，支持子类继承
        assertNotNull("CacheEvict must be @Inherited", inherited);
    }

    // ─── 边界场景 ───

    @Test
    public void testKey_EmptyArray_MeansClearCacheGroup() throws NoSuchMethodException {
        Method method = CacheEvictTest.class.getDeclaredMethod("evictAllKeys");
        CacheEvict annotation = method.getAnnotation(CacheEvict.class);

        // Given: @CacheEvict(name="product")，key 为空数组
        // Then: 空 key 数组表示语义"清空整个 product 缓存分组"
        assertEquals(0, annotation.key().length);
        assertEquals("name should still be set", "product", annotation.name());
    }

    @Test
    public void testCache_EmptyString_MeansUseDefaultCacheInstance() throws NoSuchMethodException {
        Method method = CacheEvictTest.class.getDeclaredMethod("evictAllKeys");
        CacheEvict annotation = method.getAnnotation(CacheEvict.class);

        // Given: cache 使用默认值 ""
        // Then: cache="" 表示清除默认缓存实例中的数据
        assertEquals("cache='' means use default cache instance", "", annotation.cache());
    }

    @Test
    public void testAnnotationTypeName_MatchesExpected() {
        // Then: 全限定名必须匹配框架预期
        assertEquals("CacheEvict class name",
                "cn.hg.aifei.cache.annotation.CacheEvict",
                CacheEvict.class.getName());
    }

    // ─── 组合使用场景（验证与 CacheInterceptor 的协作） ───

    @Test
    public void testEvictAllKeys_readableOverDefaultKey() throws NoSuchMethodException {
        // Given: @CacheEvict(name="product") - 不指定 key
        Method method = CacheEvictTest.class.getDeclaredMethod("evictAllKeys");
        CacheEvict annotation = method.getAnnotation(CacheEvict.class);

        // Then: key 数组为空（与 key={} 等价），语义为清除整个分组
        assertNotNull("name must be set", annotation.name());
        assertEquals("name should be 'product'", "product", annotation.name());
        assertEquals("key array must be empty for clear-all semantic", 0, annotation.key().length);
    }
}
