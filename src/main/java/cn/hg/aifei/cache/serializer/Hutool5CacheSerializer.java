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

package cn.hg.aifei.cache.serializer;

import cn.hutool.json.JSONUtil;
import cn.hutool.json.JSONObject;
import java.nio.charset.StandardCharsets;
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
     * 通过解析包装 JSON 中的类型信息，调用 JSONUtil.toBean 进行精确还原。
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
            // 将 value 部分（可能是 JSONObject）转换为目标类型的实例
            return JSONUtil.toBean(JSONUtil.parseObj(valueObj), clazz);
        } catch (ClassNotFoundException e) {
            throw new CacheOperationException("无法找到类型: " + typeName, e);
        }
    }
}
