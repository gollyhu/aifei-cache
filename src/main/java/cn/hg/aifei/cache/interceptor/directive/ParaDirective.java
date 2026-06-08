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

package cn.hg.aifei.cache.interceptor.directive;

import cn.aifei.enjoy.Directive;
import cn.aifei.enjoy.Env;
import cn.aifei.enjoy.TemplateException;
import cn.aifei.enjoy.expr.ast.Const;
import cn.aifei.enjoy.expr.ast.Expr;
import cn.aifei.enjoy.expr.ast.ExprList;
import cn.aifei.enjoy.expr.ast.Id;
import cn.aifei.enjoy.io.Writer;
import cn.aifei.enjoy.stat.ParseException;
import cn.aifei.enjoy.stat.Scope;
import cn.hg.aifei.cache.interceptor.KeyContext;

import java.io.IOException;

/**
 * #p() 指令 — 获取方法参数值。
 *
 * <pre>
 * 用法：
 *   #p()          — 拼接所有非 null 方法参数
 *   #p("name")    — 按参数名获取
 *   #p(id)        — 变量解析后获取
 * </pre>
 */
public class ParaDirective extends Directive {

    @Override
    public void setExprList(ExprList exprList) {
        if (exprList.length() > 1) {
            throw new ParseException("#p directive support 0 or 1 parameter only", location);
        }
        super.setExprList(exprList);
    }

    @Override
    public void exec(Env env, Scope scope, Writer writer) {
        KeyContext ctx = KeyContext.get();
        if (ctx == null) {
            throw new TemplateException("#p directive requires KeyContext in thread", location);
        }

        try {
            if (exprList.length() == 0) {
                // #p() — 拼接所有参数
                writer.write(concatArgs(ctx.args));
            } else {
                writer.write(singleArg(scope, ctx));
            }
        } catch (IOException e) {
            throw new TemplateException(e.getMessage(), location, e);
        }
    }

    private String singleArg(Scope scope, KeyContext ctx) {
        Expr first = exprList.getFirstExpr();

        // #p(0) — 整数索引
        if (first instanceof Const && ((Const) first).isInt()) {
            int index = ((Const) first).getInt();
            if (index >= 0 && index < ctx.args.length) {
                return toKeyString(ctx.args[index]);
            }
            return "";
        }

        // #p("name") — 字符串参数名
        if (first instanceof Const && ((Const) first).isStr()) {
            String name = ((Const) first).getStr();
            Object val = ctx.getArgByName(name);
            return val != null ? toKeyString(val) : "";
        }

        // #p(id) — Scope 变量引用
        if (first instanceof Id) {
            String id = ((Id) first).getId();
            // 先尝试从 Scope 获取
            Object val = first.eval(scope);
            if (val != null) {
                return toKeyString(val);
            }
            // 再尝试按参数名查找
            Object arg = ctx.getArgByName(id);
            return arg != null ? toKeyString(arg) : "";
        }

        // 其他表达式：求值后序列化
        Object val = first.eval(scope);
        return val != null ? toKeyString(val) : "";
    }

    private String concatArgs(Object[] args) {
        if (args == null || args.length == 0) return "";
        StringBuilder sb = new StringBuilder();
        for (Object arg : args) {
            if (arg != null) {
                if (sb.length() > 0) sb.append("-");
                sb.append(toKeyString(arg));
            }
        }
        return sb.toString();
    }

    static String toKeyString(Object value) {
        if (value instanceof String) return (String) value;
        if (value instanceof Number || value instanceof Boolean || value instanceof Character) {
            return String.valueOf(value);
        }
        try {
            return com.alibaba.fastjson2.JSON.toJSONString(value);
        } catch (Exception e) {
            return value.toString();
        }
    }
}
