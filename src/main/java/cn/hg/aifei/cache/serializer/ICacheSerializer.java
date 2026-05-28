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

/**
 * 缓存序列化器接口，定义对象与字节数组之间的转换规范。
 */
public interface ICacheSerializer {

    /**
     * 将对象序列化为字节数组
     *
     * @param value 待序列化对象
     * @return 字节数组，null 表示未序列化
     */
    byte[] serialize(Object value);

    /**
     * 将字节数组反序列化为对象
     *
     * @param bytes 待反序列化字节数组
     * @return 原始对象
     */
    Object deserialize(byte[] bytes);
}
