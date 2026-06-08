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

package cn.hg.aifei.cache.serializer;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import cn.hg.aifei.cache.api.CacheOperationException;

/**
 * 基于 Hutool-json 的缓存序列化器实现。
 * 通过包装对象来存储类型信息，使得反序列化时可以恢复原始类型。
 */
public class Hutool5CacheSerializer implements ICacheSerializer {

    private static final String KEY_TYPE = "type";
    private static final String KEY_VALUE = "value";

    /**
     * 序列化：将对象转换为字节数组。
     * 通过包装 JSON 存储类型和值信息，以解决 Hutool 默认不记录类型的问题。
     */
    @Override
    public byte[] serialize(Object value) {
        if (value == null) {
            return new byte[0];
        }
        // 构建包装 JSON: {"type": "com.example.User", "value": {...}}
        JSONObject wrapper = JSONUtil.createObj()
                .set(KEY_TYPE, value.getClass().getName())
                .set(KEY_VALUE, value);
        return JSONUtil.toJsonStr(wrapper).getBytes(StandardCharsets.UTF_8);
    }

    /**
     * 反序列化：将字节数组恢复为原始对象。
     * <p>
     * {@code valueObj} 来自 {@link JSONObject#get(String)}，已是解析后的 Java 对象：
     * <ul>
     *   <li>String/Number/Boolean → 直接作为基础类型返回（需精确匹配数值类型）</li>
     *   <li>JSONObject → 原始值为 Map/POJO，转为目标类型实例</li>
     *   <li>JSONArray → 原始值为 List/Set 等集合，转为 {@code ArrayList}</li>
     * </ul>
     * </p>
     */
    @Override
    public Object deserialize(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            return null;
        }
        String jsonStr = new String(bytes, StandardCharsets.UTF_8);
        JSONObject wrapper = JSONUtil.parseObj(jsonStr);
        String typeName = wrapper.getStr(KEY_TYPE);
        Object valueObj = wrapper.get(KEY_VALUE);
        try {
            Class<?> clazz = Class.forName(typeName);

            // JSONObject → 原始值为 Map/POJO
            if (valueObj instanceof JSONObject) {
                // Map 类型：JSONObject 本身实现了 Map<String, Object>，直接转型
                if (Map.class.isAssignableFrom(clazz)) {
                    return (Map<?, ?>) valueObj;
                }
                // POJO 类型：通过 setter 反射注入属性
                return JSONUtil.toBean((JSONObject) valueObj, clazz);
            }
            // JSONArray → 原始值为 List/Set/数组，转换为 ArrayList
            if (valueObj instanceof JSONArray) {
                return ((JSONArray) valueObj).toList(Object.class);
            }
            // Number → 确保数值类型精确匹配（解决 Hutool 对 Long/Integer 的类型推断问题）
            if (valueObj instanceof Number) {
                return convertNumber((Number) valueObj, clazz);
            }
            // String / Boolean 等基础类型直接返回
            return valueObj;
        } catch (ClassNotFoundException e) {
            throw new CacheOperationException("无法找到类型: " + typeName, e);
        }
    }

    /**
     * 根据目标类型精确转换 Number 子类，防止 Long↔Integer 类型降级导致的精度丢失。
     */
    private static Object convertNumber(Number number, Class<?> targetType) {
        if (targetType == Long.class || targetType == long.class) {
            return number.longValue();
        }
        if (targetType == Integer.class || targetType == int.class) {
            return number.intValue();
        }
        if (targetType == Double.class || targetType == double.class) {
            return number.doubleValue();
        }
        if (targetType == Float.class || targetType == float.class) {
            return number.floatValue();
        }
        if (targetType == Short.class || targetType == short.class) {
            return number.shortValue();
        }
        if (targetType == Byte.class || targetType == byte.class) {
            return number.byteValue();
        }
        return number;
    }
}
