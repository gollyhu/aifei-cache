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

import cn.hg.aifei.cache.api.CacheOperationException;
import org.dromara.hutool.json.JSONUtil;
import org.dromara.hutool.json.JSONObject;
import java.nio.charset.StandardCharsets;

/**
 * 基于 Hutool-json（6.x）的缓存序列化器实现。
 * 通过包装对象来存储类型信息，使得反序列化时可以恢复原始类型。
 */
public class Hutool6CacheSerializer implements ICacheSerializer {

    private static final String KEY_TYPE = "type";
    private static final String KEY_VALUE = "value";

    /**
     * 序列化：将对象转换为字节数组。
     */
    @Override
    public byte[] serialize(Object value) {
        if (value == null) {
            return new byte[0];
        }
        // 构建包装 JSON: {"type": "com.example.User", "value": {...}}
        JSONObject wrapper = JSONUtil.ofObj()
                .putValue(KEY_TYPE, value.getClass().getName())
                .putValue(KEY_VALUE, value);
        return JSONUtil.toJsonStr(wrapper).getBytes(StandardCharsets.UTF_8);
    }

    /**
     * 反序列化：将字节数组恢复为原始对象。
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
            // 注意：如果 valueObj 是 JSONPrimitive，toBean 仍能正常工作
            return JSONUtil.toBean(JSONUtil.parseObj(valueObj), clazz);
        } catch (ClassNotFoundException e) {
            throw new CacheOperationException("无法找到类型: " + typeName, e);
        }
    }

}