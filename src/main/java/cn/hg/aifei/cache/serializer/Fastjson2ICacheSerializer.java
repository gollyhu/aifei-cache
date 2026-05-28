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

package cn.hg.aifei.cache.serializer;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONReader;
import com.alibaba.fastjson2.JSONWriter;

/**
 * 基于 Fastjson2 的缓存序列化器，支持任意 Java 对象（需有默认构造函数）
 */
public class Fastjson2ICacheSerializer implements ICacheSerializer {

    /**
     * 序列化：将对象转为字节数组，同时写入类名以便准确反序列化
     */
    @Override
    public byte[] serialize(Object value) {
        if (value == null) {
            return null;
        }
        // 使用 WriteClassName 写入全限定类名
        return JSON.toJSONBytes(value, JSONWriter.Feature.WriteClassName);
    }

    /**
     * 反序列化：从字节数组还原对象，使用写入的类名自动识别类型
     */
    @Override
    public Object deserialize(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            return null;
        }
        // 开启 SupportAutoType 以使用写入的类名
        return JSON.parseObject(bytes, Object.class, JSONReader.Feature.SupportAutoType);
    }
}
