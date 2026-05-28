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

package cn.hg.aifei.cache;

import cn.aifei.log.Log;
import cn.aifei.log.LogFactory;

import java.util.function.Supplier;

public class DummyLogFactory implements LogFactory {
    static Log dummyLog = new Log() {
        @Override
        public String getName() {
            return "";
        }

        @Override
        public boolean isTraceEnabled() {
            return false;
        }

        @Override
        public void trace(String msg, Throwable t) {

        }

        @Override
        public void trace(String msg) {

        }

        @Override
        public void trace(String format, Object arg) {

        }

        @Override
        public void trace(String format, Object arg1, Object arg2) {

        }

        @Override
        public void trace(String format, Object... arguments) {

        }

        @Override
        public void trace(Supplier<String> messageSupplier) {

        }

        @Override
        public boolean isDebugEnabled() {
            return false;
        }

        @Override
        public void debug(String msg, Throwable t) {

        }

        @Override
        public void debug(String msg) {

        }

        @Override
        public void debug(String format, Object arg) {

        }

        @Override
        public void debug(String format, Object arg1, Object arg2) {

        }

        @Override
        public void debug(String format, Object... arguments) {

        }

        @Override
        public void debug(Supplier<String> messageSupplier) {

        }

        @Override
        public boolean isInfoEnabled() {
            return false;
        }

        @Override
        public void info(String msg, Throwable t) {

        }

        @Override
        public void info(String msg) {

        }

        @Override
        public void info(String format, Object arg) {

        }

        @Override
        public void info(String format, Object arg1, Object arg2) {

        }

        @Override
        public void info(String format, Object... arguments) {

        }

        @Override
        public void info(Supplier<String> messageSupplier) {

        }

        @Override
        public boolean isWarnEnabled() {
            return false;
        }

        @Override
        public void warn(String msg, Throwable t) {

        }

        @Override
        public void warn(String msg) {

        }

        @Override
        public void warn(String format, Object arg) {

        }

        @Override
        public void warn(String format, Object arg1, Object arg2) {

        }

        @Override
        public void warn(String format, Object... arguments) {

        }

        @Override
        public void warn(Supplier<String> messageSupplier) {

        }

        @Override
        public boolean isErrorEnabled() {
            return false;
        }

        @Override
        public void error(String msg, Throwable t) {

        }

        @Override
        public void error(String msg) {

        }

        @Override
        public void error(String format, Object arg) {

        }

        @Override
        public void error(String format, Object arg1, Object arg2) {

        }

        @Override
        public void error(String format, Object... arguments) {

        }

        @Override
        public void error(Supplier<String> messageSupplier) {

        }


    };

    @Override
    public Log getLog() {
        return dummyLog;
    }

    @Override
    public Log getLog(Class<?> clazz) {
        return dummyLog;
    }

    @Override
    public Log getLog(String name) {
        return dummyLog;
    }
}
