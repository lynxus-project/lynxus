package io.github.lynxus.test.jdbc;

import org.junit.jupiter.api.Test;
import io.github.lynxus.JdbcAssembly;
import io.github.lynxus.api.ConnectionHandle;
import io.github.lynxus.api.ConnectionHandleFactory;
import io.github.lynxus.api.ExecutionInterceptor;
import io.github.lynxus.api.SqlExecutor;
import io.github.lynxus.api.ExecutionOutcome;
import io.github.lynxus.api.ExecutionPhase;
import io.github.lynxus.api.ExecutionPlan;
import io.github.lynxus.api.JdbcExecutionState;
import io.github.lynxus.api.RowCursor;
import io.github.lynxus.api.SqlExecutionException;


import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JdbcCursorExecutionTest {

    @Test
    void readsRowsLazilyAndInvalidatesCursorAfterCallback() {
        List<String> events = new ArrayList<>();
        ResultSet rows = rows(events, List.of("Alice", "Bob"));
        AtomicReference<RowCursor<String>> captured = new AtomicReference<>();
        SqlExecutor executor = executor(events, statement(events, rows));

        String first = executor.queryCursor(plan(), (RowCursor<String> cursor) -> {
            captured.set(cursor);
            assertEquals(true, cursor.next());
            return cursor.current();
        });

        assertEquals("Alice", first);
        assertEquals(1, events.stream().filter("rows.next"::equals).count());
        assertEquals(List.of("rows.close", "statement.close", "transaction.close"),
            events.subList(events.size() - 3, events.size()));
        assertThrows(IllegalStateException.class, captured.get()::next);
        assertThrows(IllegalStateException.class, captured.get()::current);
    }

    @Test
    void callbackFailureStillClosesEveryOwnedResource() {
        List<String> events = new ArrayList<>();
        SqlExecutor executor = executor(events, statement(events, rows(events, List.of("Alice"))));

        assertThrows(IllegalArgumentException.class, () -> executor.queryCursor(
            plan(), (RowCursor<String> cursor) -> {
            cursor.next();
            throw new IllegalArgumentException("stop");
        }));

        assertEquals(List.of("rows.close", "statement.close", "transaction.close"),
            events.subList(events.size() - 3, events.size()));
    }

    @Test
    void cleanupFailureProducesOneFinalCursorOutcomeAfterResourceRelease() {
        List<String> events = new ArrayList<>();
        SQLException closeFailure = new SQLException("statement close failed");
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
        SqlExecutor executor = executor(
            events,
            statement(events, rows(events, List.of("Alice")), closeFailure),
            List.of(interceptor));

        SqlExecutionException failure = assertThrows(SqlExecutionException.class, () ->
            executor.queryCursor(plan(), (RowCursor<String> cursor) -> {
                cursor.next();
                return cursor.current();
            }));

        assertSame(failure, observedOutcome.get().failure());
        assertSame(closeFailure, failure.getCause());
        assertEquals(ExecutionPhase.CLEANUP, failure.getPhase());
        assertEquals(JdbcExecutionState.EXECUTED, failure.getExecutionState());
        assertEquals(0, observedOutcome.get().resultCount());
        assertEquals(List.of(
            "before", "transaction.open", "transaction.connection", "connection.prepare",
            "rows.next", "rows.close", "statement.close", "transaction.close", "failure:CLEANUP"
        ), events);
    }

    @Test
    void callbackRuntimeFailureRemainsPrimaryWhenCursorCleanupAlsoFails() {
        List<String> events = new ArrayList<>();
        IllegalArgumentException callbackFailure = new IllegalArgumentException("stop");
        SQLException closeFailure = new SQLException("statement close failed");
        AtomicReference<ExecutionOutcome> observedOutcome = new AtomicReference<>();
        ExecutionInterceptor interceptor = new ExecutionInterceptor() {
            @Override
            public void afterFailure(ExecutionOutcome outcome) {
                observedOutcome.set(outcome);
            }
        };
        SqlExecutor executor = executor(
            events,
            statement(events, rows(events, List.of("Alice")), closeFailure),
            List.of(interceptor));

        IllegalArgumentException deliveredFailure = assertThrows(IllegalArgumentException.class, () ->
            executor.queryCursor(plan(), (RowCursor<String> cursor) -> {
                cursor.next();
                throw callbackFailure;
            }));

        assertSame(callbackFailure, deliveredFailure);
        assertSame(callbackFailure, observedOutcome.get().failure());
        assertArrayEquals(new Throwable[]{closeFailure}, callbackFailure.getSuppressed());
        assertEquals(0, observedOutcome.get().resultCount());
        assertEquals(List.of("rows.close", "statement.close", "transaction.close"),
            events.subList(events.size() - 3, events.size()));
    }

    @Test
    void rejectsNonSelectAndMissingRowMapperBeforeOpeningConnection() {
        List<String> events = new ArrayList<>();
        SqlExecutor executor = executor(events, statement(events, rows(events, List.of("Alice"))));
        ExecutionPlan update = new ExecutionPlan(
            "test.Mapper.update", "UPDATE users SET name = 'x'", new Object[0],
            ExecutionPlan.StatementType.UPDATE, ExecutionPlan.SqlSource.ANNOTATION,
            null, null, resultSet -> resultSet.getString(1));
        ExecutionPlan missingMapper = new ExecutionPlan(
            "test.Mapper.find", "SELECT name FROM users", new Object[0],
            ExecutionPlan.StatementType.SELECT, ExecutionPlan.SqlSource.ANNOTATION);

        assertThrows(IllegalArgumentException.class, () -> executor.queryCursor(update, cursor -> null));
        assertThrows(IllegalArgumentException.class, () -> executor.queryCursor(missingMapper, cursor -> null));
        assertEquals(List.of(), events);
    }

    private ExecutionPlan plan() {
        return new ExecutionPlan(
            "test.Mapper.scan", "SELECT name FROM users", new Object[0],
            ExecutionPlan.StatementType.SELECT, ExecutionPlan.SqlSource.ANNOTATION,
            null, null, resultSet -> resultSet.getString(1));
    }

    private SqlExecutor executor(List<String> events, PreparedStatement statement) {
        return executor(events, statement, List.of());
    }

    private SqlExecutor executor(
            List<String> events, PreparedStatement statement, List<ExecutionInterceptor> interceptors) {
        Connection connection = proxy(Connection.class, (method, args) -> {
            if (method.equals("prepareStatement")) {
                events.add("connection.prepare");
                return statement;
            }
            return null;
        });
        ConnectionHandleFactory factory = () -> new ConnectionHandle() {
            @Override
            public Connection connection() {
                events.add("transaction.connection");
                return connection;
            }

            @Override
            public void close() {
                events.add("transaction.close");
            }
        };
        return JdbcAssembly.sqlExecutor(() -> {
            events.add("transaction.open");
            return factory.openHandle();
        }, interceptors);
    }

    private PreparedStatement statement(List<String> events, ResultSet rows) {
        return statement(events, rows, null);
    }

    private PreparedStatement statement(List<String> events, ResultSet rows, SQLException closeFailure) {
        return proxy(PreparedStatement.class, (method, args) -> switch (method) {
            case "executeQuery" -> rows;
            case "close" -> {
                events.add("statement.close");
                if (closeFailure != null) {
                    throw closeFailure;
                }
                yield null;
            }
            default -> null;
        });
    }

    private ResultSet rows(List<String> events, List<String> values) {
        int[] index = {-1};
        return proxy(ResultSet.class, (method, args) -> switch (method) {
            case "next" -> { events.add("rows.next"); yield ++index[0] < values.size(); }
            case "getString" -> values.get(index[0]);
            case "close" -> { events.add("rows.close"); yield null; }
            default -> null;
        });
    }

    @SuppressWarnings("unchecked")
    private <T> T proxy(Class<T> type, Invocation invocation) {
        return (T) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{type},
            (proxy, method, args) -> invocation.invoke(method.getName(), args == null ? new Object[0] : args));
    }

    @FunctionalInterface
    private interface Invocation {
        Object invoke(String method, Object[] args) throws Throwable;
    }
}
