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
 * distribute under the License is distribute on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package cn.hg.aifei.cache.api;

/**
 * 缓存连接异常
 * <p>
 * 表示缓存服务连接失败、网络超时等连接层面的问题。
 * 上层调用方可据此决定降级策略（如回退到本地缓存）。
 *
 * @author aifei
 */
public class CacheConnectionException extends CacheException {

    public CacheConnectionException(String message) {
        super(message);
    }

    public CacheConnectionException(String message, Throwable cause) {
        super(message, cause);
    }
}
