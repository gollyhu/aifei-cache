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

package cn.hg.aifei.cache.interceptor;

import cn.aifei.core.Input;
import cn.aifei.enjoy.Engine;
import cn.aifei.enjoy.Template;
import cn.aifei.log.Log;
import cn.hg.aifei.cache.interceptor.directive.ParaDirective;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

/**
 * 缓存 Key 生成器 — 基于 aifei Enjoy 模板引擎 + 自定义指令。
 *
 * <h3>支持的指令</h3>
 * <table>
 *   <tr><th>指令</th><th>说明</th></tr>
 *   <tr><td>{@code #para()}</td><td>拼接所有方法参数（复杂类型转 JSON）</td></tr>
 *   <tr><td>{@code #para(0)}</td><td>按参数索引获取</td></tr>
 *   <tr><td>{@code #para("name")}</td><td>按参数名称获取</td></tr>
 *   <tr><td>{@code #para(id)}</td><td>Scope 变量解析后序列化</td></tr>
 * </table>
 *
 * @author aifei
 */
public class KeyGenerator {

    private static final Log LOG = Log.get(KeyGenerator.class);

    /** 独立的 Enjoy 引擎实例 */
    private static final Engine ENGINE;

    static {
        ENGINE = Engine.create("cacheKeyEngine");
        ENGINE.setToClassPathSourceFactory();
        ENGINE.addDirective("para", ParaDirective.class);
        ENGINE.addDirective("p", ParaDirective.class);
    }

    /**
     * 根据表达式模板生成缓存 Key。
     *
     * @param expression 表达式模板，如 "prod-#para(id)"
     * @param method     目标方法
     * @param args       方法参数值
     * @param input      HTTP 请求输入（可为 null）
     * @return 计算后的缓存 Key
     */
    public static String generate(String expression, Method method, Object[] args, Input input) {
        if (expression == null || expression.isEmpty()) {
            throw new IllegalArgumentException("Cache key expression must not be null or empty");
        }

        KeyContext ctx = new KeyContext(method, args, input, KeyContext.buildParamIndex(method));
        KeyContext.set(ctx);
        try {
            // 构建 Scope：方法参数名 → 值
            Map<String, Object> scope = buildScope(ctx);
            try {
                Template template = ENGINE.getTemplateByString(expression);
                String result = template.renderToString(scope);

                if (LOG.isDebugEnabled()) {
                    LOG.debug("Generated cache key: expression='{}' -> key='{}'", expression, result);
                }
                return result;
            } catch (cn.aifei.enjoy.stat.ParseException e) {
                LOG.warn("Failed to parse key expression '{}', using raw expression: {}", expression, e.getMessage());
                return expression;
            }
        } finally {
            KeyContext.remove();
        }
    }

    /**
     * 构建 Enjoy Scope：参数名 → 参数值。
     */
    private static Map<String, Object> buildScope(KeyContext ctx) {
        Map<String, Object> scope = new HashMap<>();
        if (ctx.args != null) {
            for (Map.Entry<String, Integer> entry : ctx.paramIndex.entrySet()) {
                int idx = entry.getValue();
                if (idx < ctx.args.length) {
                    scope.put(entry.getKey(), ctx.args[idx]);
                }
            }
        }
        return scope;
    }
}
