/*
 * Copyright 2015 the original author or authors.
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
package org.glowroot.agent.it.harness.impl;

import java.io.Closeable;
import java.io.IOException;
import java.util.Set;
import java.util.concurrent.Executors;

import javax.annotation.Nullable;

import com.google.common.collect.Sets;
import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.glowroot.agent.AgentModule;
import org.glowroot.agent.MainEntryPoint;
import org.glowroot.agent.it.harness.AppUnderTest;
import org.glowroot.agent.it.harness.Threads;
import org.glowroot.agent.it.harness.grpc.Common.Void;
import org.glowroot.agent.it.harness.grpc.JavaagentServiceGrpc.JavaagentService;
import org.glowroot.agent.it.harness.grpc.JavaagentServiceOuterClass.AppUnderTestClassName;
import org.glowroot.agent.it.harness.grpc.JavaagentServiceOuterClass.ExpectedLogMessage;
import org.glowroot.agent.it.harness.grpc.JavaagentServiceOuterClass.LogCount;
import org.glowroot.agent.util.SpyingLogbackFilter;

public class JavaagentServiceImpl implements JavaagentService {

    private static final Logger logger = LoggerFactory.getLogger(JavaagentServiceImpl.class);

    private volatile @Nullable Thread executingAppThread;

    private volatile @Nullable Set<Thread> preExistingThreads;

    private volatile @Nullable Closeable serverCloseable;

    @Override
    public void ping(Void request, StreamObserver<Void> responseObserver) {
        responseObserver.onNext(Void.getDefaultInstance());
        responseObserver.onCompleted();
    }

    @Override
    public void executeApp(AppUnderTestClassName request, StreamObserver<Void> responseObserver) {
        if (preExistingThreads == null) {
            preExistingThreads = Sets.newHashSet(Threads.currentThreads());
        }
        if (executingAppThread != null) {
            throw new IllegalStateException("Already executing an app");
        }
        try {
            executingAppThread = Thread.currentThread();
            Class<?> appClass = Class.forName(request.getValue());
            AppUnderTest app = (AppUnderTest) appClass.newInstance();
            app.executeApp();
        } catch (Throwable t) {
            logger.error(t.getMessage(), t);
            responseObserver.onError(t);
            return;
        } finally {
            executingAppThread = null;
        }
        responseObserver.onNext(Void.getDefaultInstance());
        responseObserver.onCompleted();
    }

    @Override
    public void interruptApp(Void request, StreamObserver<Void> responseObserver) {
        Thread thread = executingAppThread;
        if (thread != null) {
            try {
                thread.interrupt();
            } catch (Throwable t) {
                logger.error(t.getMessage(), t);
                responseObserver.onError(t);
                return;
            }
        }
        responseObserver.onNext(Void.getDefaultInstance());
        responseObserver.onCompleted();
    }

    @Override
    public void addExpectedLogMessage(ExpectedLogMessage request,
            StreamObserver<Void> responseObserver) {
        try {
            SpyingLogbackFilter.addExpectedLogMessage(request.getLoggerName(),
                    request.getPartialMessage());
        } catch (Throwable t) {
            logger.error(t.getMessage(), t);
            responseObserver.onError(t);
            return;
        }
        responseObserver.onNext(Void.getDefaultInstance());
        responseObserver.onCompleted();
    }

    @Override
    public void clearLogMessages(Void request, StreamObserver<LogCount> responseObserver) {
        org.glowroot.agent.util.SpyingLogbackFilter.LogCount logCount;
        try {
            logCount = SpyingLogbackFilter.clearMessages();
        } catch (Throwable t) {
            logger.error(t.getMessage(), t);
            responseObserver.onError(t);
            return;
        }
        responseObserver.onNext(LogCount.newBuilder()
                .setUnexpectedCount(logCount.unexpectedCount())
                .setExpectedButNotLoggedCount(logCount.expectedButNotLoggedCount())
                .build());
        responseObserver.onCompleted();
    }

    @Override
    public void resetAllConfig(Void request, StreamObserver<Void> responseObserver) {
        try {
            AgentModule agentModule = MainEntryPoint.getGlowrootAgentInit().getAgentModule();
            agentModule.getConfigService().resetAllConfig();
            agentModule.getLiveWeavingService().reweave("");
        } catch (Throwable t) {
            logger.error(t.getMessage(), t);
            responseObserver.onError(t);
            return;
        }
        responseObserver.onNext(Void.getDefaultInstance());
        responseObserver.onCompleted();
    }

    @Override
    public void shutdown(Void request, StreamObserver<Void> responseObserver) {
        try {
            if (preExistingThreads != null) {
                Threads.preShutdownCheck(preExistingThreads);
            }
            MainEntryPoint.getGlowrootAgentInit().close();
            if (preExistingThreads != null) {
                Threads.postShutdownCheck(preExistingThreads);
            }
        } catch (Throwable t) {
            logger.error(t.getMessage(), t);
            responseObserver.onError(t);
            return;
        }
        responseObserver.onNext(Void.getDefaultInstance());
        responseObserver.onCompleted();
    }

    @Override
    public void kill(Void request, StreamObserver<Void> responseObserver) {
        responseObserver.onNext(Void.getDefaultInstance());
        responseObserver.onCompleted();
        Executors.newSingleThreadExecutor().execute(new Runnable() {
            @Override
            public void run() {
                try {
                    // wait a few millis for response to be returned
                    Thread.sleep(10);
                } catch (InterruptedException e) {
                }
                try {
                    serverCloseable.close();
                } catch (IOException e) {
                    logger.error(e.getMessage(), e);
                }
                System.exit(0);
            }
        });
    }

    public void setServerCloseable(Closeable serverCloseable) {
        this.serverCloseable = serverCloseable;
    }
}
