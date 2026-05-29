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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;

/**
 * 基于 JDK 原生序列化的缓存序列化器
 * <p>
 * 要求缓存值必须实现 {@link Serializable} 接口，适用于简单的 Java 对象缓存场景。
 */
public class JdkCacheSerializer implements ICacheSerializer {

    @Override
    public byte[] serialize(Object value) {
        if (value == null) {
            throw new IllegalArgumentException("Redis cache value can not be null");
        }
        if (!(value instanceof Serializable)) {
            throw new IllegalArgumentException("Redis cache value must implement Serializable: " + value.getClass().getName());
        }
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (ObjectOutputStream out = new ObjectOutputStream(bytes)) {
                out.writeObject(value);
            }
            return bytes.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize cache value", e);
        }
    }

    @Override
    public Object deserialize(byte[] bytes) {
        if (bytes == null) {
            throw new IllegalArgumentException("bytes can not be null");
        }
        try {
            try (ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(bytes))) {
                return in.readObject();
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to deserialize cache value", e);
        }
    }
}
