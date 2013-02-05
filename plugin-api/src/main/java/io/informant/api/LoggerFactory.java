/**
 * Copyright 2011-2013 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.informant.api;

import checkers.igj.quals.Immutable;
import checkers.nullness.quals.Nullable;

/**
 * This is simply a wrapper of the SLF4J Logger API without the Marker support.
 * 
 * By using this wrapper, Informant is able to use either shaded or unshaded slf4j binding, as well
 * as log messages to its embedded H2 database so the log messages can be displayed via the user
 * interface.
 * 
 * @author Trask Stalnaker
 * @since 0.5
 */
public abstract class LoggerFactory {

    private static final String LOGGER_FACTORY_IMPL_CLASS_NAME =
            "io.informant.core.log.LoggerFactoryImpl";

    @Nullable
    private static final LoggerFactory instance;

    static {
        instance = getLoggerFactoryInstance();
    }

    protected LoggerFactory() {}

    protected abstract Logger getLogger(String name);

    public static Logger getLogger(Class<?> type) {
        if (instance == null) {
            return new LoggerImpl(type);
        }
        return instance.getLogger(type.getName());
    }

    @Nullable
    private static LoggerFactory getLoggerFactoryInstance() {
        Class<?> loggerFactoryImplClass;
        try {
            loggerFactoryImplClass = Class.forName(LOGGER_FACTORY_IMPL_CLASS_NAME);
        } catch (ClassNotFoundException e) {
            // this happens during the plugin-api unit tests at which time
            // io.informant.core.LoggerFactoryImpl is not available
            return null;
        }
        try {
            return (LoggerFactory) loggerFactoryImplClass.newInstance();
        } catch (InstantiationException e) {
            // this really really really shouldn't happen, but anyways, couldn't load the logger so
            // best recourse at this point is to write error to stderr
            e.printStackTrace();
            throw new IllegalStateException(e);
        } catch (IllegalAccessException e) {
            // this really really really shouldn't happen, but anyways, couldn't load the logger so
            // best recourse at this point is to write error to stderr
            e.printStackTrace();
            throw new IllegalStateException(e);
        }
    }

    // this is used during the plugin-api unit tests at which time
    // io.informant.core.LoggerFactoryImpl is not available
    @Immutable
    private static class LoggerImpl implements Logger {

        private final org.slf4j.Logger logger;

        private LoggerImpl(Class<?> type) {
            this.logger = org.slf4j.LoggerFactory.getLogger(type);
        }
        public boolean isTraceEnabled() {
            return logger.isTraceEnabled();
        }
        public void trace(String msg) {
            logger.trace(msg);
        }
        public void trace(String format, @Nullable Object arg) {
            logger.trace(format, arg);
        }
        public void trace(String format, @Nullable Object arg1, @Nullable Object arg2) {
            logger.trace(format, arg1, arg2);
        }
        public void trace(String format, @Nullable Object... arguments) {
            logger.trace(format, arguments);
        }
        public void trace(@Nullable String msg, Throwable t) {
            logger.trace(msg, t);
        }
        public boolean isDebugEnabled() {
            return logger.isDebugEnabled();
        }
        public void debug(String msg) {
            logger.debug(msg);
        }
        public void debug(String format, @Nullable Object arg) {
            logger.debug(format, arg);
        }
        public void debug(String format, @Nullable Object arg1, @Nullable Object arg2) {
            logger.debug(format, arg1, arg2);
        }
        public void debug(String format, @Nullable Object... arguments) {
            logger.debug(format, arguments);
        }
        public void debug(@Nullable String msg, Throwable t) {
            logger.debug(msg, t);
        }
        public boolean isInfoEnabled() {
            return logger.isInfoEnabled();
        }
        public void info(String msg) {
            logger.info(msg);
        }
        public void info(String format, @Nullable Object arg) {
            logger.info(format, arg);
        }
        public void info(String format, @Nullable Object arg1, @Nullable Object arg2) {
            logger.info(format, arg1, arg2);
        }
        public void info(String format, @Nullable Object... arguments) {
            logger.info(format, arguments);
        }
        public void info(@Nullable String msg, Throwable t) {
            logger.info(msg, t);
        }
        public boolean isWarnEnabled() {
            return logger.isWarnEnabled();
        }
        public void warn(String msg) {
            logger.warn(msg);
        }
        public void warn(String format, @Nullable Object arg) {
            logger.warn(format, arg);
        }
        public void warn(String format, @Nullable Object arg1, @Nullable Object arg2) {
            logger.warn(format, arg1, arg2);
        }
        public void warn(String format, @Nullable Object... arguments) {
            logger.warn(format, arguments);
        }
        public void warn(@Nullable String msg, Throwable t) {
            logger.warn(msg, t);
        }
        public boolean isErrorEnabled() {
            return logger.isErrorEnabled();
        }
        public void error(String msg) {
            logger.error(msg);
        }
        public void error(String format, @Nullable Object arg) {
            logger.error(format, arg);
        }
        public void error(String format, @Nullable Object arg1, @Nullable Object arg2) {
            logger.error(format, arg1, arg2);
        }
        public void error(String format, @Nullable Object... arguments) {
            logger.error(format, arguments);
        }
        public void error(@Nullable String msg, Throwable t) {
            logger.error(msg, t);
        }
    }
}
