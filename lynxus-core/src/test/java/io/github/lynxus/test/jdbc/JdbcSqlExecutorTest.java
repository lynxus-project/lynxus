package io.github.lynxus.test.jdbc;

import org.junit.jupiter.api.Test;
import io.github.lynxus.JdbcAssembly;
import io.github.lynxus.api.BatchExecutionPlan;
import io.github.lynxus.api.ConnectionHandle;
import io.github.lynxus.api.SqlExecutor;
import io.github.lynxus.api.ConnectionHandleFactory;
import io.github.lynxus.api.ExecutionInterceptor;
import io.github.lynxus.api.ExecutionOutcome;
import io.github.lynxus.api.ExecutionPhase;
import io.github.lynxus.api.ExecutionPlan;
import io.github.lynxus.api.ExecutionPlugin;
import io.github.lynxus.api.GeneratedKeyResult;
import io.github.lynxus.api.JdbcExecutionState;
import io.github.lynxus.api.ParameterBinder;
import io.github.lynxus.api.QueryExecutionPlan;
import io.github.lynxus.api.QueryResult;
import io.github.lynxus.api.ResultColumn;
import io.github.lynxus.api.StatementOptions;
import io.github.lynxus.api.SqlExecutionException;
import io.github.lynxus.api.SqlResult;
import io.github.lynxus.api.UpdateResult;
import io.github.lynxus.api.BatchResult;
import io.github.lynxus.jdbc.JdbcSqlExecutor;

import java.lang.reflect.Proxy;
import java.sql.BatchUpdateException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JdbcSqlExecutorTest {

    @Test
    void appliesConfiguredStatementOptionsBeforeBindingAndExecution() {
        List<String> events = new ArrayList<>();
        ResultSet rows = rows(events, List.<Object[]>of(new Object[]{1L, "Alice"}));
        PreparedStatement statement = statement(events, rows, 0, null, null);
        ExecutionPlan plan = new ExecutionPlan(
            "test.Mapper.find", "SELECT id, name FROM users WHERE id = ?",
            new Object[]{7L}, ExecutionPlan.StatementType.SELECT, ExecutionPlan.SqlSource.XML,
            null, null, null, new StatementOptions(3, 100, 25));

        executor(events, statement).execute(plan);

        assertEquals(List.of(
            "setQueryTimeout:3", "setFetchSize:100", "setMaxRows:25",
            "setObject:1:7", "executeQuery"
        ), events.subList(3, 8));
    }

    @Test
    void defaultStatementOptionsDoNotCallJdbcSetters() {
        List<String> events = new ArrayList<>();
        ResultSet rows = rows(events, List.<Object[]>of(new Object[]{1L, "Alice"}));
        PreparedStatement statement = statement(events, rows, 0, null, null);

        executor(events, statement).execute(selectPlan(null));

        assertFalse(events.stream().anyMatch(event -> event.startsWith("setQueryTimeout:")));
        assertFalse(events.stream().anyMatch(event -> event.startsWith("setFetchSize:")));
        assertFalse(events.stream().anyMatch(event -> event.startsWith("setMaxRows:")));
    }

    @Test
    void defaultParameterBindingDoesNotInspectTheDatabaseProduct() {
        List<String> events = new ArrayList<>();
        PreparedStatement statement = statement(events, null, 1, null, null);
        UUID value = UUID.randomUUID();
        ExecutionPlan plan = new ExecutionPlan(
            "test.Mapper.write", "INSERT INTO values_table(value) VALUES (?)", new Object[]{value},
            ExecutionPlan.StatementType.INSERT, ExecutionPlan.SqlSource.GENERATED);

        executor(events, statement).execute(plan);

        assertTrue(events.contains("setObject:1:" + value));
    }

    @Test
    void rejectsInvalidStatementOptions() {
        assertThrows(IllegalArgumentException.class, () -> new StatementOptions(0, null, null));
        assertThrows(IllegalArgumentException.class, () -> new StatementOptions(-1, null, null));
        assertThrows(IllegalArgumentException.class, () -> new StatementOptions(null, 0, null));
        assertThrows(IllegalArgumentException.class, () -> new StatementOptions(null, -1, null));
        assertThrows(IllegalArgumentException.class, () -> new StatementOptions(null, null, -1));

        assertEquals(new StatementOptions(1, 1, 0), new StatementOptions(1, 1, 0));
    }

    @Test
    void validatesBeforeOpeningTransaction() {
        TrackingFactory transactions = new TrackingFactory(null, new ArrayList<>());
        ExecutionPlan invalid = new ExecutionPlan(
            "test.Mapper.batch", "INSERT INTO values_table VALUES (?)", new Object[0],
            ExecutionPlan.StatementType.BATCH, ExecutionPlan.SqlSource.GENERATED);

        assertThrows(IllegalArgumentException.class, () ->
            new JdbcSqlExecutor(transactions).execute(invalid));

        assertEquals(0, transactions.openCount);
    }

    @Test
    void selectsRawRowsAndClosesResourcesInOwnershipOrder() {
        List<String> events = new ArrayList<>();
        ResultSet rows = rows(events, List.of(new Object[]{1L, "Alice"}, new Object[]{2L, "Bob"}));
        PreparedStatement statement = statement(events, rows, 0, null, null);
        JdbcSqlExecutor executor = executor(events, statement);

        SqlResult<Object[]> result = rawQueryResult(executor.execute(selectPlan(null)));

        assertArrayEquals(new Object[]{1L, "Alice"}, result.getQueryResults().get(0));
        assertArrayEquals(new Object[]{2L, "Bob"}, result.getQueryResults().get(1));
        assertEquals(List.of(
            "transaction.open", "transaction.connection", "connection.prepare",
            "setObject:1:7", "executeQuery",
            "rows.next:true", "rows.getObject:1", "rows.getObject:2",
            "rows.next:true", "rows.getObject:1", "rows.getObject:2", "rows.next:false",
            "rows.close", "statement.close", "transaction.close"
        ), events);
    }

    @Test
    void capturesResultColumnLabelsOnceBeforeReadingRows() {
        List<String> events = new ArrayList<>();
        ResultSet rows = rows(
            events,
            List.of("USER_NAME", "id"),
            List.<Object[]>of(new Object[]{"Alice", 7L}));
        PreparedStatement statement = statement(events, rows, 0, null, null);

        SqlResult<Object[]> result = rawQueryResult(executor(events, statement).execute(selectPlan(null)));

        assertEquals(List.of(new ResultColumn("USER_NAME", 0), new ResultColumn("id", 1)),
            result.getResultColumns());
        assertEquals(0, result.requireColumnIndex("user_name"));
        assertEquals(1, result.requireColumnIndex("ID"));
        assertEquals(1, events.stream().filter("metadata.getColumnLabel:1"::equals).count());
        assertEquals(1, events.stream().filter("metadata.getColumnLabel:2"::equals).count());
    }

    @Test
    void assemblesTypedQueryResultsInGeneratedMappingOrder() {
        List<String> events = new ArrayList<>();
        ResultSet rows = rows(
            events,
            List.of("name", "id"),
            List.<Object[]>of(new Object[]{"Alice", "7"}));
        PreparedStatement statement = statement(events, rows, 0, null, null);
        QueryExecutionPlan<MappedUser> plan = new QueryExecutionPlan<>(
            "test.Mapper.find", "SELECT name, id FROM users", new Object[0],
            ExecutionPlan.StatementType.SELECT, ExecutionPlan.SqlSource.GENERATED,
            null, null, null,
            row -> new MappedUser((String) row.get(0), (String) row.get(1)),
            null,
            new ExecutionPlan.TypeRouting(
                new Class<?>[0], null,
                new Class<?>[]{String.class, String.class},
                new String[]{"id", "name"}));

        SqlResult<MappedUser> result = executor(events, statement).execute(plan);

        assertEquals(List.of(new MappedUser("7", "Alice")), result.getQueryResults());
        assertTrue(events.indexOf("rows.getString:2") < events.indexOf("rows.close"));
    }

    @Test
    void typedEntryPointsReuseTheSingleJdbcExecutionLifecycle() {
        List<String> queryEvents = new ArrayList<>();
        QueryExecutionPlan<String> queryPlan = new QueryExecutionPlan<>(
            "test.Mapper.find", "SELECT name FROM users", new Object[0],
            ExecutionPlan.StatementType.SELECT, ExecutionPlan.SqlSource.GENERATED,
            null, null, resultSet -> resultSet.getString(1), null, null, null);
        QueryResult<String> queryResult = executor(
            queryEvents,
            statement(queryEvents, singleColumnRows(queryEvents, List.of("Alice")), 0, null, null))
            .query(queryPlan);

        assertEquals(List.of("Alice"), queryResult.rows());
        assertEquals(1, queryEvents.stream().filter("transaction.open"::equals).count());
        assertEquals(1, queryEvents.stream().filter("transaction.close"::equals).count());

        List<String> updateEvents = new ArrayList<>();
        UpdateResult updateResult = executor(
            updateEvents,
            statement(updateEvents, null, 3, null, null))
            .update(writePlan(ExecutionPlan.StatementType.UPDATE, false, null));
        assertEquals(3, updateResult.count());
        assertTrue(updateEvents.contains("executeUpdate"));

        List<String> keyEvents = new ArrayList<>();
        GeneratedKeyResult<Long> keyResult = executor(
            keyEvents,
            statement(keyEvents, null, 1, generatedKeys(keyEvents, 42L), null))
            .generatedKey(writePlan(ExecutionPlan.StatementType.INSERT, true, null));
        assertEquals(42L, keyResult.key());
        assertTrue(keyEvents.contains("getGeneratedKeys"));

        List<String> batchEvents = new ArrayList<>();
        BatchExecutionPlan batchPlan = new BatchExecutionPlan(
            "test.Mapper.insertAll", "INSERT INTO users(name) VALUES (?)",
            List.<Object[]>of(new Object[]{"Alice"}), ExecutionPlan.SqlSource.GENERATED);
        BatchResult batchResult = executor(
            batchEvents,
            statement(batchEvents, null, 0, null, new int[]{1}))
            .batch(batchPlan);
        assertArrayEquals(new int[]{1}, batchResult.counts());
        assertTrue(batchEvents.contains("executeBatch"));
    }

    @Test
    void executesInsertUpdateAndDeleteAsJdbcUpdates() {
        for (ExecutionPlan.StatementType type : List.of(
                ExecutionPlan.StatementType.INSERT,
                ExecutionPlan.StatementType.UPDATE,
                ExecutionPlan.StatementType.DELETE)) {
            List<String> events = new ArrayList<>();
            PreparedStatement statement = statement(events, null, 3, null, null);

            SqlResult<?> result = executor(events, statement).execute(writePlan(type, false, null));

            assertEquals(3, result.getUpdateCount());
            assertEquals(1, events.stream().filter("executeUpdate"::equals).count());
        }
    }

    @Test
    void requestsAndExtractsExactlyOneGeneratedKey() {
        List<String> events = new ArrayList<>();
        ResultSet keys = generatedKeys(events, 42L);
        PreparedStatement statement = statement(events, null, 1, keys, null);

        SqlResult<?> result = executor(events, statement).execute(
            writePlan(ExecutionPlan.StatementType.INSERT, true, null));

        assertEquals(42L, result.getGeneratedKey());
        assertEquals("connection.prepare:[id]",
            events.stream().filter(event -> event.startsWith("connection.prepare:")).findFirst().orElseThrow());
        assertEquals(List.of("keys.close", "statement.close", "transaction.close"),
            events.subList(events.size() - 3, events.size()));
    }

    @Test
    void rejectsMissingAndMultipleGeneratedKeyRows() {
        for (List<?> keyRows : List.of(List.of(), List.of(1L, 2L))) {
            List<String> events = new ArrayList<>();
            PreparedStatement statement = statement(
                events, null, 1, generatedKeys(events, keyRows), null);
            AtomicReference<ExecutionOutcome> observedOutcome = new AtomicReference<>();
            ExecutionInterceptor interceptor = new ExecutionInterceptor() {
                @Override
                public void afterFailure(ExecutionOutcome outcome) {
                    observedOutcome.set(outcome);
                }
            };
            SqlExecutor executor = observing(
                new TrackingFactory(connection(events, statement), events), List.of(interceptor));

            SqlExecutionException failure = assertThrows(SqlExecutionException.class, () ->
                executor.execute(
                    writePlan(ExecutionPlan.StatementType.INSERT, true, null)));

            SQLException cause = assertInstanceOf(SQLException.class, failure.getCause());
            assertEquals(keyRows.isEmpty()
                ? "JDBC returned no generated key"
                : "JDBC returned multiple generated keys for one insert", cause.getMessage());
            assertEquals(0, observedOutcome.get().affectedRows());
            assertEquals(List.of("keys.close", "statement.close", "transaction.close"),
                events.subList(events.size() - 3, events.size()));
        }
    }

    @Test
    void mapsGeneratedKeyWithConfiguredRowMapper() {
        List<String> events = new ArrayList<>();
        String keyValue = "7dc53df5-703e-49b3-8670-b1c468f47f1f";
        PreparedStatement statement = statement(
            events, null, 1, generatedKeys(events, List.of(keyValue)), null);
        ExecutionPlan plan = new ExecutionPlan(
            "test.Mapper.insert", "INSERT INTO users(name) VALUES (?)", new Object[]{"Alice"},
            ExecutionPlan.StatementType.INSERT, ExecutionPlan.SqlSource.ANNOTATION,
            "id", null, resultSet -> UUID.fromString(resultSet.getString(1)));

        SqlResult<?> result = executor(events, statement).execute(plan);

        assertEquals(UUID.fromString(keyValue), result.getGeneratedKey());
        assertEquals(1, events.stream().filter("keys.getString:1"::equals).count());
        assertEquals(1, events.stream().filter("connection.prepare:[id]"::equals).count());
    }

    @Test
    void rejectsCompositeGeneratedKeyRows() {
        List<String> events = new ArrayList<>();
        PreparedStatement statement = statement(
            events, null, 1, generatedKeys(events, List.of(1L), 2), null);

        SqlExecutionException failure = assertThrows(SqlExecutionException.class, () ->
            executor(events, statement).execute(
                writePlan(ExecutionPlan.StatementType.INSERT, true, null)));

        SQLException cause = assertInstanceOf(SQLException.class, failure.getCause());
        assertEquals("JDBC returned a composite generated key with 2 columns", cause.getMessage());
    }

    @Test
    void bindsEveryBatchRowAndExecutesOneBatch() {
        List<String> events = new ArrayList<>();
        PreparedStatement statement = statement(events, null, 0, null, new int[]{1, 1});
        BatchExecutionPlan plan = new BatchExecutionPlan(
            "test.Mapper.insertAll", "INSERT INTO users(id, name) VALUES (?, ?)",
            List.of(new Object[]{1L, "Alice"}, new Object[]{2L, "Bob"}),
            ExecutionPlan.SqlSource.GENERATED);

        SqlResult<?> result = executor(events, statement).execute(plan);

        assertArrayEquals(new int[]{1, 1}, result.getBatchUpdateCounts());
        assertEquals(List.of(
            "setObject:1:1", "setObject:2:Alice", "addBatch",
            "setObject:1:2", "setObject:2:Bob", "addBatch", "executeBatch"
        ), events.subList(3, 10));
    }

    @Test
    void emptyBatchReturnsEmptyCountsWithoutJdbcExecution() {
        List<String> events = new ArrayList<>();
        PreparedStatement statement = statement(events, null, 0, null, new int[]{99});
        BatchExecutionPlan plan = new BatchExecutionPlan(
            "test.Mapper.insertAll", "INSERT INTO users(id) VALUES (?)",
            List.of(), ExecutionPlan.SqlSource.GENERATED);

        SqlResult<?> result = executor(events, statement).execute(plan);

        assertArrayEquals(new int[0], result.getBatchUpdateCounts());
        assertFalse(events.contains("executeBatch"));
    }

    @Test
    void emptyBatchCleanupFailureRemainsNotExecuted() {
        List<String> events = new ArrayList<>();
        SQLException closeFailure = new SQLException("statement close failed");
        PreparedStatement statement = statement(events, null, 0, null, new int[]{99}, closeFailure);
        BatchExecutionPlan plan = new BatchExecutionPlan(
            "test.Mapper.insertAll", "INSERT INTO users(id) VALUES (?)",
            List.of(), ExecutionPlan.SqlSource.GENERATED);

        SqlExecutionException failure = assertThrows(SqlExecutionException.class, () ->
            executor(events, statement).execute(plan));

        assertSame(closeFailure, failure.getCause());
        assertEquals(ExecutionPhase.CLEANUP, failure.getPhase());
        assertEquals(JdbcExecutionState.NOT_EXECUTED, failure.getExecutionState());
        assertFalse(events.contains("executeBatch"));
    }

    @Test
    void usesCustomBinderAndRowMapperWithoutReflection() {
        List<String> bindEvents = new ArrayList<>();
        PreparedStatement writeStatement = statement(bindEvents, null, 1, null, null);
        ParameterBinder<String> binder = (target, index, value) -> {
            bindEvents.add("binder:" + index + ":" + value);
            target.setNull(index, Types.VARCHAR);
        };

        executor(bindEvents, writeStatement).execute(
            writePlan(ExecutionPlan.StatementType.INSERT, false, binder));

        assertEquals(List.of("binder:1:null", "setNull:1:" + Types.VARCHAR),
            bindEvents.subList(3, 5));

        List<String> rowEvents = new ArrayList<>();
        ResultSet resultSet = singleColumnRows(rowEvents, List.of("Alice", "Bob"));
        SqlResult<Object[]> result = rawQueryResult(
            executor(rowEvents, statement(rowEvents, resultSet, 0, null, null))
                .execute(selectPlan(current -> current.getString(1).toUpperCase())));

        assertArrayEquals(new Object[]{"ALICE"}, result.getQueryResults().get(0));
        assertArrayEquals(new Object[]{"BOB"}, result.getQueryResults().get(1));
    }

    @Test
    void invokesInterceptorsAroundExecutionInDeterministicOrder() {
        List<String> events = new ArrayList<>();
        ExecutionInterceptor first = interceptor("first", events);
        ExecutionInterceptor second = interceptor("second", events);
        PreparedStatement statement = statement(new ArrayList<>(), null, 3, null, null);
        SqlExecutor executor = observing(
            new TrackingFactory(connection(new ArrayList<>(), statement), new ArrayList<>()),
            List.of(first, second));

        executor.execute(writePlan(ExecutionPlan.StatementType.UPDATE, false, null));

        assertEquals(List.of("first.before", "second.before", "second.success:3", "first.success:3"), events);
    }

    @Test
    void terminalSuccessCallbackFailureDoesNotChangeTheSqlResult() {
        List<String> events = new ArrayList<>();
        ExecutionInterceptor first = interceptor("first", events);
        ExecutionInterceptor failing = new ExecutionInterceptor() {
            @Override
            public void beforeExecution(ExecutionPlan plan) {
                events.add("failing.before");
            }

            @Override
            public void afterSuccess(ExecutionOutcome outcome) {
                events.add("failing.success");
                throw new IllegalStateException("observer failed");
            }
        };
        PreparedStatement statement = statement(new ArrayList<>(), null, 3, null, null);
        SqlExecutor executor = observing(
            new TrackingFactory(connection(new ArrayList<>(), statement), new ArrayList<>()),
            List.of(first, failing));

        SqlResult<?> result = executor.execute(writePlan(ExecutionPlan.StatementType.UPDATE, false, null));

        assertEquals(3, result.getUpdateCount());
        assertEquals(List.of(
            "first.before", "failing.before", "failing.success", "first.success:3"
        ), events);
    }

    @Test
    void terminalSuccessCallbackErrorPropagates() {
        AssertionError callbackError = new AssertionError("observer error");
        ExecutionInterceptor failing = new ExecutionInterceptor() {
            @Override
            public void afterSuccess(ExecutionOutcome outcome) {
                throw callbackError;
            }
        };
        PreparedStatement statement = statement(new ArrayList<>(), null, 3, null, null);
        SqlExecutor executor = observing(
            new TrackingFactory(connection(new ArrayList<>(), statement), new ArrayList<>()),
            List.of(failing));

        AssertionError deliveredError = assertThrows(AssertionError.class, () ->
            executor.execute(writePlan(ExecutionPlan.StatementType.UPDATE, false, null)));

        assertSame(callbackError, deliveredError);
    }

    @Test
    void terminalSuccessErrorDoesNotInvokeAfterFailure() {
        List<String> events = new ArrayList<>();
        AssertionError callbackError = new AssertionError("observer error");
        ExecutionInterceptor failing = new ExecutionInterceptor() {
            @Override
            public void afterSuccess(ExecutionOutcome outcome) {
                events.add("success");
                throw callbackError;
            }

            @Override
            public void afterFailure(ExecutionOutcome outcome) {
                events.add("failure");
            }
        };
        PreparedStatement statement = statement(new ArrayList<>(), null, 3, null, null);
        SqlExecutor executor = observing(
            new TrackingFactory(connection(new ArrayList<>(), statement), new ArrayList<>()),
            List.of(failing));

        AssertionError deliveredError = assertThrows(AssertionError.class, () ->
            executor.execute(writePlan(ExecutionPlan.StatementType.UPDATE, false, null)));

        assertSame(callbackError, deliveredError);
        assertEquals(List.of("success"), events);
    }

    @Test
    void emptyBatchObserverSeesNotExecuted() {
        AtomicReference<JdbcExecutionState> state = new AtomicReference<>();
        PreparedStatement statement = statement(new ArrayList<>(), null, 0, null, new int[]{99});
        ExecutionInterceptor observer = new ExecutionInterceptor() {
            @Override
            public void afterSuccess(ExecutionOutcome outcome) {
                state.set(outcome.executionState());
            }
        };
        SqlExecutor executor = observing(
            new TrackingFactory(connection(new ArrayList<>(), statement), new ArrayList<>()),
            List.of(observer));

        SqlResult<?> result = executor.execute(new BatchExecutionPlan(
            "test.Mapper.insertAll", "INSERT INTO users(id) VALUES (?)",
            List.of(), ExecutionPlan.SqlSource.GENERATED));

        assertArrayEquals(new int[0], result.getBatchUpdateCounts());
        assertEquals(JdbcExecutionState.NOT_EXECUTED, state.get());
    }

    @Test
    void pluginThrowBeforeNextReportsNotExecuted() {
        AtomicReference<JdbcExecutionState> state = new AtomicReference<>();
        ExecutionInterceptor observer = new ExecutionInterceptor() {
            @Override
            public void afterFailure(ExecutionOutcome outcome) {
                state.set(outcome.executionState());
            }
        };
        ExecutionPlugin plugin = (plan, next) -> {
            throw new IllegalStateException("plugin veto");
        };

        assertThrows(RuntimeException.class, () ->
            JdbcAssembly.sqlExecutor(
                () -> {
                    throw new IllegalStateException("JDBC must not run");
                },
                List.of(observer),
                List.of(plugin))
                .execute(selectPlan(null)));

        assertEquals(JdbcExecutionState.NOT_EXECUTED, state.get());
    }

    @Test
    void terminalFailureCallbackErrorPropagates() {
        AssertionError callbackError = new AssertionError("observer error");
        ExecutionInterceptor failing = new ExecutionInterceptor() {
            @Override
            public void afterFailure(ExecutionOutcome outcome) {
                throw callbackError;
            }
        };
        ConnectionHandleFactory transactions = () -> new ConnectionHandle() {
            @Override
            public Connection connection() {
                throw new IllegalStateException("connection failed");
            }

            @Override
            public void close() {
            }
        };

        AssertionError deliveredError = assertThrows(AssertionError.class, () ->
            observing(transactions, List.of(failing)).execute(selectPlan(null)));

        assertSame(callbackError, deliveredError);
    }

    @Test
    void outcomeDurationIncludesCleanupAndExcludesTerminalCallbackWork() {
        long workNanos = 2_000_000L;
        AtomicLong cleanupDuration = new AtomicLong();
        AtomicLong callbackDuration = new AtomicLong();
        AtomicReference<ExecutionOutcome> observedOutcome = new AtomicReference<>();
        PreparedStatement statement = statement(new ArrayList<>(), null, 3, null, null);
        Connection connection = connection(new ArrayList<>(), statement);
        ConnectionHandleFactory transactions = () -> new ConnectionHandle() {
            @Override
            public Connection connection() {
                return connection;
            }

            @Override
            public void close() {
                long startedAt = System.nanoTime();
                doWorkFor(workNanos);
                cleanupDuration.set(System.nanoTime() - startedAt);
            }
        };
        ExecutionInterceptor interceptor = new ExecutionInterceptor() {
            @Override
            public void afterSuccess(ExecutionOutcome outcome) {
                observedOutcome.set(outcome);
                long startedAt = System.nanoTime();
                doWorkFor(workNanos);
                callbackDuration.set(System.nanoTime() - startedAt);
            }
        };
        SqlExecutor executor = observing(transactions, List.of(interceptor));

        long invocationStartedAt = System.nanoTime();
        executor.execute(writePlan(ExecutionPlan.StatementType.UPDATE, false, null));
        long invocationDuration = System.nanoTime() - invocationStartedAt;

        assertTrue(observedOutcome.get().durationNanos() >= cleanupDuration.get());
        assertTrue(invocationDuration - observedOutcome.get().durationNanos()
            >= callbackDuration.get());
    }

    @Test
    void beforeFailureUnwindsOnlyInterceptorsThatEnteredSuccessfully() {
        List<String> events = new ArrayList<>();
        ExecutionInterceptor first = interceptor("first", events);
        ExecutionInterceptor failing = new ExecutionInterceptor() {
            @Override
            public void beforeExecution(ExecutionPlan plan) {
                events.add("failing.before");
                throw new IllegalStateException("veto");
            }

            @Override
            public void afterFailure(ExecutionOutcome outcome) {
                events.add("failing.failure");
            }
        };

        SqlExecutionException failure = assertThrows(SqlExecutionException.class, () ->
            observing(() -> null, List.of(first, failing)).execute(selectPlan(null)));

        assertEquals(JdbcExecutionState.NOT_EXECUTED, failure.getExecutionState());
        assertEquals(ExecutionPhase.PREPARATION, failure.getPhase());
        assertEquals(List.of("first.before", "failing.before", "first.failure"), events);
    }

    @Test
    void unwindsFailureInterceptorsInReverseOrder() {
        List<String> events = new ArrayList<>();
        ExecutionInterceptor first = interceptor("first", events);
        ExecutionInterceptor second = interceptor("second", events);
        ConnectionHandleFactory transactions = () -> new ConnectionHandle() {
            @Override public Connection connection() { throw new IllegalStateException("failed"); }
            @Override public void close() { }
        };

        SqlExecutionException failure = assertThrows(SqlExecutionException.class, () ->
            observing(transactions, List.of(first, second)).execute(selectPlan(null)));

        assertEquals(JdbcExecutionState.NOT_EXECUTED, failure.getExecutionState());
        assertEquals(ExecutionPhase.PREPARATION, failure.getPhase());
        assertEquals(List.of("first.before", "second.before", "second.failure", "first.failure"), events);
    }

    @Test
    void reportsBindingPhaseWhenParameterBindingFails() {
        SQLException bindFailure = new SQLException("bind failed");
        PreparedStatement statement = proxy(PreparedStatement.class, (method, args) -> switch (method) {
            case "setObject" -> throw bindFailure;
            case "close" -> null;
            default -> null;
        });

        SqlExecutionException failure = assertThrows(SqlExecutionException.class, () ->
            executor(new ArrayList<>(), statement)
                .execute(writePlan(ExecutionPlan.StatementType.UPDATE, false, null)));

        assertSame(bindFailure, failure.getCause());
        assertEquals(ExecutionPhase.BINDING, failure.getPhase());
        assertEquals(JdbcExecutionState.NOT_EXECUTED, failure.getExecutionState());
    }

    @Test
    void reportsUnknownOutcomeWhenJdbcExecuteThrows() {
        SQLException executeFailure = new SQLException("execute failed");
        PreparedStatement statement = proxy(PreparedStatement.class, (method, args) -> switch (method) {
            case "setObject", "close" -> null;
            case "executeUpdate" -> throw executeFailure;
            default -> null;
        });

        SqlExecutionException failure = assertThrows(SqlExecutionException.class, () ->
            executor(new ArrayList<>(), statement)
                .execute(writePlan(ExecutionPlan.StatementType.UPDATE, false, null)));

        assertSame(executeFailure, failure.getCause());
        assertEquals(ExecutionPhase.EXECUTION, failure.getPhase());
        assertEquals(JdbcExecutionState.OUTCOME_UNKNOWN, failure.getExecutionState());
    }

    @Test
    void preservesBatchUpdateCountsWhenDriverReportsPartialFailure() {
        BatchUpdateException batchFailure = new BatchUpdateException(
            "batch failed", "23505", 0, new int[]{1, Statement.EXECUTE_FAILED});
        PreparedStatement statement = proxy(PreparedStatement.class, (method, args) -> switch (method) {
            case "setObject", "addBatch", "close" -> null;
            case "executeBatch" -> throw batchFailure;
            default -> null;
        });
        BatchExecutionPlan plan = new BatchExecutionPlan(
            "test.Mapper.insertAll", "INSERT INTO users(id, name) VALUES (?, ?)",
            List.of(new Object[]{1L, "Alice"}, new Object[]{2L, "Bob"}),
            ExecutionPlan.SqlSource.GENERATED);

        SqlExecutionException failure = assertThrows(SqlExecutionException.class, () ->
            executor(new ArrayList<>(), statement).execute(plan));

        BatchUpdateException cause = assertInstanceOf(BatchUpdateException.class, failure.getCause());
        assertSame(batchFailure, cause);
        assertArrayEquals(new int[]{1, Statement.EXECUTE_FAILED}, cause.getUpdateCounts());
        assertEquals(ExecutionPhase.EXECUTION, failure.getPhase());
        assertEquals(JdbcExecutionState.OUTCOME_UNKNOWN, failure.getExecutionState());
    }

    @Test
    void reportsUnknownOutcomeWhenDriverCancelsExecution() {
        SQLException cancellation = new SQLException("statement cancelled", "57014");
        PreparedStatement statement = proxy(PreparedStatement.class, (method, args) -> switch (method) {
            case "setObject", "close" -> null;
            case "executeUpdate" -> throw cancellation;
            default -> null;
        });

        SqlExecutionException failure = assertThrows(SqlExecutionException.class, () ->
            executor(new ArrayList<>(), statement)
                .execute(writePlan(ExecutionPlan.StatementType.UPDATE, false, null)));

        SQLException cause = assertInstanceOf(SQLException.class, failure.getCause());
        assertSame(cancellation, cause);
        assertEquals("57014", cause.getSQLState());
        assertEquals(ExecutionPhase.EXECUTION, failure.getPhase());
        assertEquals(JdbcExecutionState.OUTCOME_UNKNOWN, failure.getExecutionState());
    }

    @Test
    void reportsExecutedWhenCleanupFailsAfterJdbcReturns() {
        SQLException closeFailure = new SQLException("statement close failed");
        PreparedStatement statement = statement(new ArrayList<>(), null, 1, null, null, closeFailure);

        SqlExecutionException failure = assertThrows(SqlExecutionException.class, () ->
            executor(new ArrayList<>(), statement)
                .execute(writePlan(ExecutionPlan.StatementType.UPDATE, false, null)));

        assertSame(closeFailure, failure.getCause());
        assertEquals(ExecutionPhase.CLEANUP, failure.getPhase());
        assertEquals(JdbcExecutionState.EXECUTED, failure.getExecutionState());
    }

    @Test
    void cleanupFailureProducesOneFinalFailureOutcomeAfterResourceRelease() {
        List<String> events = new ArrayList<>();
        SQLException closeFailure = new SQLException("statement close failed");
        PreparedStatement statement = statement(events, null, 3, null, null, closeFailure);
        AtomicReference<ExecutionOutcome> observedOutcome = new AtomicReference<>();
        ExecutionInterceptor interceptor = new ExecutionInterceptor() {
            @Override
            public void beforeExecution(ExecutionPlan plan) {
                events.add("before");
            }

            @Override
            public void afterSuccess(ExecutionOutcome outcome) {
                events.add("success");
            }

            @Override
            public void afterFailure(ExecutionOutcome outcome) {
                observedOutcome.set(outcome);
                SqlExecutionException failure = assertInstanceOf(
                    SqlExecutionException.class, outcome.failure());
                events.add("failure:" + failure.getPhase());
            }
        };
        SqlExecutor executor = observing(
            new TrackingFactory(connection(events, statement), events), List.of(interceptor));

        SqlExecutionException failure = assertThrows(SqlExecutionException.class, () ->
            executor.execute(writePlan(ExecutionPlan.StatementType.UPDATE, false, null)));

        assertSame(failure, observedOutcome.get().failure());
        assertSame(closeFailure, failure.getCause());
        assertEquals(JdbcExecutionState.EXECUTED, failure.getExecutionState());
        assertEquals(0, observedOutcome.get().affectedRows());
        assertEquals(List.of(
            "before", "transaction.open", "transaction.connection", "connection.prepare",
            "setObject:1:Alice", "executeUpdate", "statement.close", "transaction.close",
            "failure:CLEANUP"
        ), events);
    }

    @Test
    void preservesPrimaryFailureAndFlattensCallbackAndCleanupFailures() {
        SQLException executionFailure = new SQLException("read failed");
        SQLException resultSetCloseFailure = new SQLException("rows close failed");
        SQLException statementCloseFailure = new SQLException("statement close failed");
        IllegalStateException transactionCloseFailure = new IllegalStateException("transaction close failed");
        IllegalArgumentException callbackFailure = new IllegalArgumentException("callback failed");
        AtomicReference<ExecutionOutcome> observedOutcome = new AtomicReference<>();
        ResultSet resultSet = failingRows(executionFailure, resultSetCloseFailure);
        PreparedStatement statement = statement(new ArrayList<>(), resultSet, 0, null, null, statementCloseFailure);
        Connection connection = connection(new ArrayList<>(), statement);
        ConnectionHandleFactory transactions = () -> connectionHandle(
            connection, transactionCloseFailure, new ArrayList<>());
        ExecutionInterceptor interceptor = new ExecutionInterceptor() {
            @Override
            public void afterFailure(ExecutionOutcome outcome) {
                observedOutcome.set(outcome);
                throw callbackFailure;
            }
        };

        SqlExecutionException failure = assertThrows(SqlExecutionException.class, () ->
            observing(transactions, List.of(interceptor)).execute(selectPlan(null)));

        assertSame(executionFailure, failure.getCause());
        assertEquals(ExecutionPhase.RESULT_READING, failure.getPhase());
        assertEquals(JdbcExecutionState.EXECUTED, failure.getExecutionState());
        assertSame(failure, observedOutcome.get().failure());
        assertArrayEquals(
            new Throwable[]{resultSetCloseFailure, statementCloseFailure, transactionCloseFailure},
            executionFailure.getSuppressed());
        assertFalse(failure.getMessage().contains("customer-secret"));
    }

    @Test
    void repeatedCleanupThrowableIsPreservedOnceWithoutSelfSuppression() {
        SQLException sharedCloseFailure = new SQLException("shared close failed");
        int[] cursor = {-1};
        ResultSetMetaData metadata = proxy(ResultSetMetaData.class,
            (method, args) -> method.equals("getColumnCount") ? 1 : null);
        ResultSet resultSet = proxy(ResultSet.class, (method, args) -> switch (method) {
            case "getMetaData" -> metadata;
            case "next" -> ++cursor[0] == 0;
            case "getObject" -> "value";
            case "close" -> throw sharedCloseFailure;
            default -> null;
        });
        PreparedStatement statement = statement(
            new ArrayList<>(), resultSet, 0, null, null, sharedCloseFailure);

        SqlExecutionException failure = assertThrows(SqlExecutionException.class, () ->
            executor(new ArrayList<>(), statement).execute(selectPlan(null)));

        assertSame(sharedCloseFailure, failure.getCause());
        assertArrayEquals(new Throwable[0], sharedCloseFailure.getSuppressed());
    }

    private ExecutionInterceptor interceptor(String name, List<String> events) {
        return new ExecutionInterceptor() {
            @Override public void beforeExecution(ExecutionPlan plan) { events.add(name + ".before"); }
            @Override public void afterSuccess(ExecutionOutcome outcome) {
                events.add(name + ".success:" + outcome.affectedRows());
            }
            @Override public void afterFailure(ExecutionOutcome outcome) { events.add(name + ".failure"); }
        };
    }

    private void doWorkFor(long durationNanos) {
        long deadline = System.nanoTime() + durationNanos;
        while (System.nanoTime() < deadline) {
            Thread.onSpinWait();
        }
    }

    private SqlExecutor observing(
            ConnectionHandleFactory connectionHandleFactory, List<ExecutionInterceptor> interceptors) {
        return JdbcAssembly.sqlExecutor(connectionHandleFactory, interceptors);
    }

    private JdbcSqlExecutor executor(List<String> events, PreparedStatement statement) {
        return new JdbcSqlExecutor(new TrackingFactory(connection(events, statement), events));
    }

    private ExecutionPlan selectPlan(io.github.lynxus.api.RowMapper<?> rowMapper) {
        return new ExecutionPlan(
            "test.Mapper.find", "SELECT id, name FROM users WHERE id = ?",
            new Object[]{7L}, ExecutionPlan.StatementType.SELECT, ExecutionPlan.SqlSource.XML,
            null, null, rowMapper);
    }

    private ExecutionPlan writePlan(
            ExecutionPlan.StatementType type, boolean generatedKey, ParameterBinder<?> binder) {
        return new ExecutionPlan(
            "test.Mapper.write", "INSERT INTO users(name) VALUES (?)", new Object[]{binder == null ? "Alice" : null},
            type, ExecutionPlan.SqlSource.ANNOTATION, generatedKey ? "id" : null,
            binder == null ? null : new ParameterBinder<?>[]{binder}, null);
    }

    private Connection connection(List<String> events, PreparedStatement statement) {
        return proxy(Connection.class, (method, args) -> {
            if (method.equals("prepareStatement")) {
                events.add(args.length == 2
                    ? "connection.prepare:" + formatPrepareOption(args[1])
                    : "connection.prepare");
                return statement;
            }
            return null;
        });
    }

    private String formatPrepareOption(Object option) {
        return option instanceof String[] columns ? java.util.Arrays.toString(columns) : option.toString();
    }

    private PreparedStatement statement(
            List<String> events, ResultSet rows, int updateCount, ResultSet keys, int[] batchCounts) {
        return statement(events, rows, updateCount, keys, batchCounts, null);
    }

    private PreparedStatement statement(
            List<String> events,
            ResultSet rows,
            int updateCount,
            ResultSet keys,
            int[] batchCounts,
            SQLException closeFailure) {
        return proxy(PreparedStatement.class, (method, args) -> switch (method) {
            case "setQueryTimeout" -> { events.add("setQueryTimeout:" + args[0]); yield null; }
            case "setFetchSize" -> { events.add("setFetchSize:" + args[0]); yield null; }
            case "setMaxRows" -> { events.add("setMaxRows:" + args[0]); yield null; }
            case "setObject" -> { events.add("setObject:" + args[0] + ":" + args[1]); yield null; }
            case "setNull" -> { events.add("setNull:" + args[0] + ":" + args[1]); yield null; }
            case "executeQuery" -> { events.add("executeQuery"); yield rows; }
            case "executeUpdate" -> { events.add("executeUpdate"); yield updateCount; }
            case "getGeneratedKeys" -> { events.add("getGeneratedKeys"); yield keys; }
            case "addBatch" -> { events.add("addBatch"); yield null; }
            case "executeBatch" -> { events.add("executeBatch"); yield batchCounts; }
            case "close" -> { events.add("statement.close"); if (closeFailure != null) throw closeFailure; yield null; }
            default -> null;
        });
    }

    private ResultSet rows(List<String> events, List<Object[]> values) {
        int[] cursor = {-1};
        ResultSetMetaData metadata = proxy(ResultSetMetaData.class, (method, args) -> switch (method) {
            case "getColumnCount" -> values.get(0).length;
            case "getColumnType" -> Types.OTHER;
            default -> null;
        });
        return proxy(ResultSet.class, (method, args) -> switch (method) {
            case "getMetaData" -> metadata;
            case "next" -> { boolean present = ++cursor[0] < values.size(); events.add("rows.next:" + present); yield present; }
            case "getObject" -> { events.add("rows.getObject:" + args[0]); yield values.get(cursor[0])[(int) args[0] - 1]; }
            case "close" -> { events.add("rows.close"); yield null; }
            default -> null;
        });
    }

    private ResultSet rows(List<String> events, List<String> labels, List<Object[]> values) {
        int[] cursor = {-1};
        ResultSetMetaData metadata = proxy(ResultSetMetaData.class, (method, args) -> switch (method) {
            case "getColumnCount" -> labels.size();
            case "getColumnLabel" -> {
                events.add("metadata.getColumnLabel:" + args[0]);
                yield labels.get((int) args[0] - 1);
            }
            case "getColumnType" -> Types.VARCHAR;
            default -> null;
        });
        return proxy(ResultSet.class, (method, args) -> switch (method) {
            case "getMetaData" -> metadata;
            case "next" -> ++cursor[0] < values.size();
            case "getObject" -> values.get(cursor[0])[(int) args[0] - 1];
            case "getString" -> {
                events.add("rows.getString:" + args[0]);
                yield values.get(cursor[0])[(int) args[0] - 1];
            }
            case "close" -> { events.add("rows.close"); yield null; }
            default -> null;
        });
    }

    private ResultSet singleColumnRows(List<String> events, List<String> values) {
        int[] cursor = {-1};
        return proxy(ResultSet.class, (method, args) -> switch (method) {
            case "next" -> ++cursor[0] < values.size();
            case "getString" -> values.get(cursor[0]);
            case "close" -> { events.add("rows.close"); yield null; }
            default -> null;
        });
    }

    private ResultSet generatedKeys(List<String> events, Object key) {
        return generatedKeys(events, List.of(key));
    }

    private ResultSet generatedKeys(List<String> events, List<?> keys) {
        return generatedKeys(events, keys, 1);
    }

    private ResultSet generatedKeys(List<String> events, List<?> keys, int columnCount) {
        int[] cursor = {-1};
        ResultSetMetaData metadata = proxy(ResultSetMetaData.class,
            (method, args) -> method.equals("getColumnCount") ? columnCount : null);
        return proxy(ResultSet.class, (method, args) -> switch (method) {
            case "getMetaData" -> metadata;
            case "next" -> ++cursor[0] < keys.size();
            case "getObject" -> keys.get(cursor[0]);
            case "getString" -> {
                events.add("keys.getString:" + args[0]);
                yield keys.get(cursor[0]).toString();
            }
            case "close" -> { events.add("keys.close"); yield null; }
            default -> null;
        });
    }

    private ResultSet failingRows(SQLException readFailure, SQLException closeFailure) {
        ResultSetMetaData metadata = proxy(ResultSetMetaData.class,
            (method, args) -> method.equals("getColumnCount") ? 1 : null);
        return proxy(ResultSet.class, (method, args) -> switch (method) {
            case "getMetaData" -> metadata;
            case "next" -> true;
            case "getObject" -> throw readFailure;
            case "close" -> throw closeFailure;
            default -> null;
        });
    }

    private ConnectionHandle connectionHandle(
            Connection connection, RuntimeException closeFailure, List<String> events) {
        return new ConnectionHandle() {
            @Override public Connection connection() {
                events.add("transaction.connection");
                return connection;
            }
            @Override public void close() { events.add("transaction.close"); if (closeFailure != null) throw closeFailure; }
        };
    }

    @SuppressWarnings("unchecked")
    private SqlResult<Object[]> rawQueryResult(SqlResult<?> result) {
        return (SqlResult<Object[]>) result;
    }

    @SuppressWarnings("unchecked")
    private <T> T proxy(Class<T> type, Invocation action) {
        return (T) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{type}, (proxy, method, args) -> {
            Object result = action.invoke(method.getName(), args == null ? new Object[0] : args);
            if (result != null || !method.getReturnType().isPrimitive()) return result;
            if (method.getReturnType() == boolean.class) return false;
            if (method.getReturnType() == byte.class) return (byte) 0;
            if (method.getReturnType() == short.class) return (short) 0;
            if (method.getReturnType() == int.class) return 0;
            if (method.getReturnType() == long.class) return 0L;
            if (method.getReturnType() == float.class) return 0F;
            if (method.getReturnType() == double.class) return 0D;
            if (method.getReturnType() == char.class) return '\0';
            return null;
        });
    }

    @FunctionalInterface
    private interface Invocation {
        Object invoke(String method, Object[] args) throws Throwable;
    }

    private record MappedUser(String id, String name) {
    }

    private final class TrackingFactory implements ConnectionHandleFactory {
        private final Connection connection;
        private final List<String> events;
        private int openCount;

        private TrackingFactory(Connection connection, List<String> events) {
            this.connection = connection;
            this.events = events;
        }

        @Override
        public ConnectionHandle openHandle() {
            openCount++;
            events.add("transaction.open");
            return connectionHandle(connection, null, events);
        }
    }
}
