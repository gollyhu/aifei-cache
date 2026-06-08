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


import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import java.io.IOException;

/**
 * 基于 Jackson 2.0.x 的缓存序列化器实现。
 * 序列化时记录类型信息，反序列化时可直接还原为原始对象类型。
 */
public class JacksonCacheSerializer implements ICacheSerializer {

    private final ObjectMapper objectMapper;

    /**
     * 使用默认配置构造序列化器。
     * <p>
     * 开启 {@link ObjectMapper.DefaultTyping#JAVA_LANG_OBJECT} 类型记录策略，
     * 配合 {@link #serialize(Object)} 中显式声明 {@code Object.class} 目标类型，
     * 确保 Long、Integer 等 final 包装类型也能写入 {@code @class} 类型信息，
     * 避免反序列化时被降级（如 Long → Integer）。
     * </p>
     */
    public JacksonCacheSerializer() {
        this.objectMapper = new ObjectMapper();
        this.objectMapper.activateDefaultTyping(
                BasicPolymorphicTypeValidator.builder()
                        .allowIfBaseType(Object.class)
                        .build(),
                ObjectMapper.DefaultTyping.JAVA_LANG_OBJECT
        );
    }

    /**
     * 允许注入自定义配置的 ObjectMapper。
     *
     * @param objectMapper 自定义的 ObjectMapper（必须已配置合适的类型处理策略）
     */
    public JacksonCacheSerializer(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public byte[] serialize(Object value) {
        if (value == null) {
            return null;
        }
        try {
            // 使用 writerFor(Object.class) 显式声明目标类型为 Object，
            // 配合 JAVA_LANG_OBJECT 策略，触发 Jackson 为所有类型（含 final 类）写入 @class 类型信息
            return objectMapper.writerFor(Object.class).writeValueAsBytes(value);
        } catch (IOException e) {
            throw new SerializationException("Jackson 序列化失败", e);
        }
    }

    @Override
    public Object deserialize(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            return null;
        }
        try {
            // 由于序列化时写入了类型信息，直接反序列化为 Object 即可恢复原始类型
            return objectMapper.readValue(bytes, Object.class);
        } catch (IOException e) {
            throw new SerializationException("Jackson 反序列化失败", e);
        }
    }

    /**
     * 序列化/反序列化过程中的非受检异常，便于上层统一处理。
     */
    public static class SerializationException extends RuntimeException {
        public SerializationException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}