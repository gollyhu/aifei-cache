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

package cn.hg.aifei.cache.api;

import java.io.Serializable;

/**
 * Null 值哨兵对象
 * <p>
 * 当启用 cache.nullValue=true 配置时，实际存储 null 值的内部占位符，
 * 用于在缓存层面区分"键不存在"和"键存在但值为 null"两种情况。
 *
 * @author aifei
 */
public final class NullValue implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 全局单例
     */
    public static final NullValue INSTANCE = new NullValue();

    /**
     * 用于基于字符串的缓存（如 Lettuce）做序列化标识
     */
    public static final String STRING_MARKER = "\0__NULL_VALUE__\0";

    private NullValue() {
    }

    /**
     * 防止反序列化产生重复实例
     */
    private Object readResolve() {
        return INSTANCE;
    }
}
