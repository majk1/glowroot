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
package org.glowroot.agent.plugin.jdbc;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Set;

import com.google.common.collect.Sets;
import org.apache.commons.dbcp.DelegatingConnection;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import org.glowroot.agent.it.harness.AppUnderTest;
import org.glowroot.agent.it.harness.Container;
import org.glowroot.agent.it.harness.Containers;
import org.glowroot.agent.it.harness.TransactionMarker;
import org.glowroot.wire.api.model.TraceOuterClass.Trace;

import static org.assertj.core.api.Assertions.assertThat;

public class CommitRollbackTest {

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
    public void testCommit() throws Exception {
        // given
        // when
        Trace trace = container.execute(ExecuteJdbcCommit.class);
        // then
        List<Trace.Entry> entries = trace.getEntryList();
        assertThat(entries).hasSize(2);
        Trace.Entry jdbcInsertEntry = entries.get(0);
        assertThat(jdbcInsertEntry.getMessage())
                .isEqualTo("jdbc execution: insert into employee (name) values ('john doe')");
        Trace.Entry jdbcCommitEntry = entries.get(1);
        assertThat(jdbcCommitEntry.getMessage()).isEqualTo("jdbc commit");
        Trace.Timer rootTimer = trace.getHeader().getRootTimer();
        assertThat(rootTimer.getName()).isEqualTo("mock trace marker");
        assertThat(rootTimer.getChildTimerList()).hasSize(2);
        // ordering is by total desc, so order is not fixed
        Set<String> childTimerNames = Sets.newHashSet();
        childTimerNames.add(rootTimer.getChildTimerList().get(0).getName());
        childTimerNames.add(rootTimer.getChildTimerList().get(1).getName());
        assertThat(childTimerNames).containsOnly("jdbc execute", "jdbc commit");
    }

    @Test
    public void testCommitThrowing() throws Exception {
        // given
        // when
        Trace trace = container.execute(ExecuteJdbcCommitThrowing.class);
        // then
        List<Trace.Entry> entries = trace.getEntryList();
        assertThat(entries).hasSize(2);
        Trace.Entry jdbcInsertEntry = entries.get(0);
        assertThat(jdbcInsertEntry.getMessage())
                .isEqualTo("jdbc execution: insert into employee (name) values ('john doe')");
        Trace.Entry jdbcCommitEntry = entries.get(1);
        assertThat(jdbcCommitEntry.getMessage()).isEqualTo("jdbc commit");
        assertThat(jdbcCommitEntry.getError().getMessage()).isEqualTo("A commit failure");
        Trace.Timer rootTimer = trace.getHeader().getRootTimer();
        assertThat(rootTimer.getName()).isEqualTo("mock trace marker");
        assertThat(rootTimer.getChildTimerList()).hasSize(2);
        // ordering is by total desc, so order is not fixed
        Set<String> childTimerNames = Sets.newHashSet();
        childTimerNames.add(rootTimer.getChildTimerList().get(0).getName());
        childTimerNames.add(rootTimer.getChildTimerList().get(1).getName());
        assertThat(childTimerNames).containsOnly("jdbc execute", "jdbc commit");
    }

    @Test
    public void testRollback() throws Exception {
        // given
        // when
        Trace trace = container.execute(ExecuteJdbcRollback.class);
        // then
        List<Trace.Entry> entries = trace.getEntryList();
        assertThat(entries).hasSize(2);
        Trace.Entry jdbcInsertEntry = entries.get(0);
        assertThat(jdbcInsertEntry.getMessage())
                .isEqualTo("jdbc execution: insert into employee (name) values ('john doe')");
        Trace.Entry jdbcCommitEntry = entries.get(1);
        assertThat(jdbcCommitEntry.getMessage()).isEqualTo("jdbc rollback");
        Trace.Timer rootTimer = trace.getHeader().getRootTimer();
        assertThat(rootTimer.getName()).isEqualTo("mock trace marker");
        assertThat(rootTimer.getChildTimerList()).hasSize(2);
        // ordering is by total desc, so order is not fixed
        Set<String> childTimerNames = Sets.newHashSet();
        childTimerNames.add(rootTimer.getChildTimerList().get(0).getName());
        childTimerNames.add(rootTimer.getChildTimerList().get(1).getName());
        assertThat(childTimerNames).containsOnly("jdbc execute", "jdbc rollback");
    }

    @Test
    public void testRollbackThrowing() throws Exception {
        // given
        // when
        Trace trace = container.execute(ExecuteJdbcRollbackThrowing.class);
        // then
        List<Trace.Entry> entries = trace.getEntryList();
        assertThat(entries).hasSize(2);
        Trace.Entry jdbcInsertEntry = entries.get(0);
        assertThat(jdbcInsertEntry.getMessage())
                .isEqualTo("jdbc execution: insert into employee (name) values ('john doe')");
        Trace.Entry jdbcCommitEntry = entries.get(1);
        assertThat(jdbcCommitEntry.getMessage()).isEqualTo("jdbc rollback");
        assertThat(jdbcCommitEntry.getError().getMessage()).isEqualTo("A rollback failure");
        Trace.Timer rootTimer = trace.getHeader().getRootTimer();
        assertThat(rootTimer.getChildTimerList()).hasSize(2);
        // ordering is by total desc, so order is not fixed
        Set<String> childTimerNames = Sets.newHashSet();
        childTimerNames.add(rootTimer.getChildTimerList().get(0).getName());
        childTimerNames.add(rootTimer.getChildTimerList().get(1).getName());
        assertThat(childTimerNames).containsOnly("jdbc execute", "jdbc rollback");
    }

    public abstract static class ExecuteJdbcCommitBase implements AppUnderTest, TransactionMarker {
        protected Connection connection;
        @Override
        public void executeApp() throws Exception {
            connection = Connections.createConnection();
            connection.setAutoCommit(false);
            try {
                transactionMarker();
            } finally {
                Connections.closeConnection(connection);
            }
        }
        void executeInsert() throws Exception {
            Statement statement = connection.createStatement();
            try {
                statement.execute("insert into employee (name) values ('john doe')");
            } finally {
                statement.close();
            }
        }
    }

    public abstract static class ExecuteJdbcCommitThrowingBase
            implements AppUnderTest, TransactionMarker {
        protected Connection connection;
        @Override
        public void executeApp() throws Exception {
            connection = new DelegatingConnection(Connections.createConnection()) {
                @Override
                public void commit() throws SQLException {
                    throw new SQLException("A commit failure");
                }
                @Override
                public void rollback() throws SQLException {
                    throw new SQLException("A rollback failure");
                }
            };
            connection.setAutoCommit(false);
            try {
                transactionMarker();
            } finally {
                Connections.closeConnection(connection);
            }
        }
        void executeInsert() throws Exception {
            Statement statement = connection.createStatement();
            try {
                statement.execute("insert into employee (name) values ('john doe')");
            } finally {
                statement.close();
            }
        }
    }

    public static class ExecuteJdbcCommit extends ExecuteJdbcCommitBase {
        @Override
        public void transactionMarker() throws Exception {
            executeInsert();
            connection.commit();
        }
    }

    public static class ExecuteJdbcRollback extends ExecuteJdbcCommitBase {
        @Override
        public void transactionMarker() throws Exception {
            executeInsert();
            connection.rollback();
        }
    }

    public static class ExecuteJdbcCommitThrowing extends ExecuteJdbcCommitThrowingBase {
        @Override
        public void transactionMarker() throws Exception {
            executeInsert();
            try {
                connection.commit();
            } catch (SQLException e) {
            }
        }
    }

    public static class ExecuteJdbcRollbackThrowing extends ExecuteJdbcCommitThrowingBase {
        @Override
        public void transactionMarker() throws Exception {
            executeInsert();
            try {
                connection.rollback();
            } catch (SQLException e) {
            }
        }
    }
}
