/*
 * Copyright 2011-2015 the original author or authors.
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
package org.glowroot.agent.tests;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import org.glowroot.agent.it.harness.AppUnderTest;
import org.glowroot.agent.it.harness.Container;
import org.glowroot.agent.it.harness.Containers;
import org.glowroot.agent.it.harness.TransactionMarker;
import org.glowroot.agent.it.harness.model.ConfigUpdate.AdvancedConfigUpdate;
import org.glowroot.wire.api.model.TraceOuterClass.Trace;

import static org.assertj.core.api.Assertions.assertThat;

public class GcActivityTest {

    private static Container container;

    @BeforeClass
    public static void setUp() throws Exception {
        container = Containers.getSharedContainer();
    }

    @AfterClass
    public static void tearDown() throws Exception {
        container.close();
    }

    @After
    public void afterEachTest() throws Exception {
        container.checkAndReset();
    }

    @Test
    public void shouldReadTraceGcActivityConfigEnabled() throws Exception {
        // given
        // when
        Trace trace = container.execute(GenerateTraceWithGcActivity.class);
        // then
        assertThat(trace.getHeader().getGcActivityList()).isNotEmpty();
    }

    @Test
    public void shouldReadTraceGcActivityConfigDisabled() throws Exception {
        // given
        container.getConfigService().updateAdvancedConfig(
                AdvancedConfigUpdate.newBuilder()
                        .setCaptureGcActivity(ProtoOptional.of(false))
                        .build());
        // when
        Trace trace = container.execute(GenerateTraceWithGcActivity.class);
        // then
        assertThat(trace.getHeader().getGcActivityList()).isEmpty();
    }

    public static class GenerateTraceWithGcActivity implements AppUnderTest, TransactionMarker {
        @Override
        public void executeApp() {
            transactionMarker();
        }
        @Override
        public void transactionMarker() {
            System.gc();
        }
    }
}
