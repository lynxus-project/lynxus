package io.github.lynxus.test.plugin;

import org.junit.jupiter.api.Test;
import io.github.lynxus.JdbcAssembly;
import io.github.lynxus.api.ConnectionHandle;
import io.github.lynxus.api.ConnectionHandleFactory;
import io.github.lynxus.api.CursorCallback;
import io.github.lynxus.api.ExecutionInterceptor;
import io.github.lynxus.api.ExecutionOutcome;
import io.github.lynxus.api.ExecutionPlan;
import io.github.lynxus.api.ExecutionPlugin;
import io.github.lynxus.api.JdbcExecutionState;
import io.github.lynxus.api.SqlExecutor;
import io.github.lynxus.api.SqlResult;
import io.github.lynxus.api.StatementOptions;

import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExecutionPluginChainTest {

    @Test
    void shortCircuitSkipsInnerObserversAndJdbc() {
        List<String> events = new ArrayList<>();
        AtomicReference<JdbcExecutionState> observedState = new AtomicReference<>();
        SqlResult<Void> cached = SqlResult.forUpdate(7);
        AtomicInteger jdbcCalls = new AtomicInteger();
        ExecutionInterceptor outer = observer("outer", events, observedState);
        ExecutionPlugin shortCircuit = (plan, next) -> {
            events.add("plugin.short-circuit");
            return cached;
        };
        ExecutionPlugin inner = (plan, next) -> {
            events.add("inner.before");
            SqlResult<?> result = next.execute(plan);
            events.add("inner.after");
            return result;
        };

        SqlResult<?> result = JdbcAssembly.sqlExecutor(
                recordingFactory(events, jdbcCalls),
                List.of(outer),
                List.of(shortCircuit, inner))
            .execute(selectPlan("SELECT 1"));

        assertSame(cached, result);
        assertEquals(0, jdbcCalls.get());
        assertEquals(JdbcExecutionState.NOT_EXECUTED, observedState.get());
        assertEquals(List.of("outer.before", "plugin.short-circuit", "outer.success"), events);
    }

    @Test
    void replacePlanExecutesReplacementSql() {
        List<String> preparedSql = new ArrayList<>();
        ExecutionPlugin replace = (plan, next) -> next.execute(new ExecutionPlan(
            plan.getStatementId(),
            "SELECT 9",
            new Object[0],
            plan.getStatementType(),
            plan.getSourceType(),
            plan.getGeneratedKeyColumn(),
            plan.getParameterBinders(),
            plan.getRowMapper(),
            plan.getStatementOptions(),
            plan.getTypeRouting()));

        SqlResult<Object[]> result = rawQueryResult(JdbcAssembly.sqlExecutor(
                factoryRecordingSql(preparedSql, 9L),
                List.of(),
                List.of(replace))
            .execute(selectPlan("SELECT 1")));

        assertEquals(List.of("SELECT 9"), preparedSql);
        assertEquals(9L, result.getQueryResults().getFirst()[0]);
    }

    @Test
    void rejectsReplacingStatementTypeBindersOrRowMapper() {
        ExecutionPlan original = selectPlan("SELECT 1");
        assertEquals(
            "plugin cannot replace statement type",
            assertThrows(IllegalArgumentException.class, () ->
                executorReplacing(plan -> new ExecutionPlan(
                    plan.getStatementId(),
                    plan.getSql(),
                    plan.getParameters(),
                    ExecutionPlan.StatementType.UPDATE,
                    plan.getSourceType()))
                    .execute(original))
                .getMessage());
        assertEquals(
            "plugin cannot replace SQL source",
            assertThrows(IllegalArgumentException.class, () ->
                executorReplacing(plan -> new ExecutionPlan(
                    plan.getStatementId(),
                    plan.getSql(),
                    plan.getParameters(),
                    plan.getStatementType(),
                    ExecutionPlan.SqlSource.XML,
                    plan.getGeneratedKeyColumn(),
                    plan.getParameterBinders(),
                    plan.getRowMapper(),
                    plan.getStatementOptions(),
                    plan.getTypeRouting()))
                    .execute(original))
                .getMessage());
        assertEquals(
            "plugin cannot replace parameter binders",
            assertThrows(IllegalArgumentException.class, () ->
                executorReplacing(plan -> new ExecutionPlan(
                    plan.getStatementId(),
                    plan.getSql(),
                    plan.getParameters(),
                    plan.getStatementType(),
                    plan.getSourceType(),
                    plan.getGeneratedKeyColumn(),
                    new io.github.lynxus.api.ParameterBinder<?>[]{(target, index, value) -> {}},
                    plan.getRowMapper(),
                    plan.getStatementOptions(),
                    plan.getTypeRouting()))
                    .execute(original))
                .getMessage());
        assertEquals(
            "plugin cannot replace row mapper",
            assertThrows(IllegalArgumentException.class, () ->
                executorReplacing(plan -> new ExecutionPlan(
                    plan.getStatementId(),
                    plan.getSql(),
                    plan.getParameters(),
                    plan.getStatementType(),
                    plan.getSourceType(),
                    plan.getGeneratedKeyColumn(),
                    plan.getParameterBinders(),
                    resultSet -> resultSet.getObject(1),
                    plan.getStatementOptions(),
                    plan.getTypeRouting()))
                    .execute(original))
                .getMessage());
        assertEquals(
            "plugin cannot replace generated-key configuration",
            assertThrows(IllegalArgumentException.class, () ->
                executorReplacing(plan -> new ExecutionPlan(
                    plan.getStatementId(),
                    plan.getSql(),
                    plan.getParameters(),
                    plan.getStatementType(),
                    plan.getSourceType(),
                    "id",
                    plan.getParameterBinders(),
                    plan.getRowMapper(),
                    plan.getStatementOptions(),
                    plan.getTypeRouting()))
                    .execute(original))
                .getMessage());
        assertEquals(
            "plugin cannot replace type routing",
            assertThrows(IllegalArgumentException.class, () ->
                executorReplacing(plan -> new ExecutionPlan(
                    plan.getStatementId(),
                    plan.getSql(),
                    plan.getParameters(),
                    plan.getStatementType(),
                    plan.getSourceType(),
                    plan.getGeneratedKeyColumn(),
                    plan.getParameterBinders(),
                    plan.getRowMapper(),
                    plan.getStatementOptions(),
                    new ExecutionPlan.TypeRouting(
                        new Class<?>[]{String.class}, null, new Class<?>[0], null)))
                    .execute(original))
                .getMessage());
    }

    private SqlExecutor executorReplacing(java.util.function.Function<ExecutionPlan, ExecutionPlan> replace) {
        return JdbcAssembly.sqlExecutor(
            unusedFactory(),
            List.of(),
            List.of((plan, next) -> next.execute(replace.apply(plan))));
    }

    @Test
    void observersAndReplacePlanPassQueryCursorThrough() {
        List<String> events = new ArrayList<>();
        List<String> preparedSql = new ArrayList<>();
        ExecutionInterceptor observer = observer("outer", events, new AtomicReference<>());
        ExecutionPlugin replace = new ExecutionPlugin() {
            @Override
            public SqlResult<?> intercept(ExecutionPlan plan, SqlExecutor next) {
                return next.execute(replacedSql(plan, "SELECT name FROM users WHERE id = 2"));
            }

            @Override
            public <T, R> R interceptCursor(
                    ExecutionPlan plan, CursorCallback<T, R> callback, SqlExecutor next) {
                return next.queryCursor(replacedSql(plan, "SELECT name FROM users WHERE id = 2"), callback);
            }
        };

        String value = JdbcAssembly.sqlExecutor(
                factoryRecordingSql(preparedSql, "Bob"),
                List.of(observer),
                List.of(replace))
            .<String, String>queryCursor(
                selectPlan("SELECT name FROM users WHERE id = 1", resultSet -> resultSet.getString(1)),
                cursor -> {
                    events.add("cursor");
                    assertTrue(cursor.next());
                    return cursor.current();
                });

        assertEquals("Bob", value);
        assertEquals(List.of("SELECT name FROM users WHERE id = 2"), preparedSql);
        assertEquals(List.of("outer.before", "cursor", "outer.success"), events);
    }

    private ExecutionPlan replacedSql(ExecutionPlan plan, String sql) {
        return new ExecutionPlan(
            plan.getStatementId(),
            sql,
            plan.getParameters(),
            plan.getStatementType(),
            plan.getSourceType(),
            plan.getGeneratedKeyColumn(),
            plan.getParameterBinders(),
            plan.getRowMapper(),
            plan.getStatementOptions(),
            plan.getTypeRouting());
    }

    private ExecutionPlan selectPlan(String sql) {
        return selectPlan(sql, null);
    }

    private ExecutionPlan selectPlan(String sql, io.github.lynxus.api.RowMapper<?> rowMapper) {
        return new ExecutionPlan(
            "test.Mapper.find",
            sql,
            new Object[0],
            ExecutionPlan.StatementType.SELECT,
            ExecutionPlan.SqlSource.GENERATED,
            null,
            null,
            rowMapper,
            StatementOptions.defaults(),
            null);
    }

    private ExecutionInterceptor observer(
            String name, List<String> events, AtomicReference<JdbcExecutionState> state) {
        return new ExecutionInterceptor() {
            @Override
            public void beforeExecution(ExecutionPlan plan) {
                events.add(name + ".before");
            }

            @Override
            public void afterSuccess(ExecutionOutcome outcome) {
                state.set(outcome.executionState());
                events.add(name + ".success");
            }
        };
    }

    private ConnectionHandleFactory recordingFactory(List<String> events, AtomicInteger jdbcCalls) {
        return () -> {
            jdbcCalls.incrementAndGet();
            events.add("jdbc");
            throw new IllegalStateException("JDBC must not run");
        };
    }

    private ConnectionHandleFactory unusedFactory() {
        return () -> {
            throw new IllegalStateException("JDBC must not run");
        };
    }

    private ConnectionHandleFactory factoryRecordingSql(List<String> preparedSql, Object cell) {
        return () -> new ConnectionHandle() {
            @Override
            public Connection connection() {
                return proxy(Connection.class, (method, arguments) -> {
                    if (method.equals("prepareStatement")) {
                        preparedSql.add((String) arguments[0]);
                        return statement(cell);
                    }
                    return null;
                });
            }

            @Override
            public void close() {
            }
        };
    }

    private PreparedStatement statement(Object cell) {
        return proxy(PreparedStatement.class, (method, arguments) -> switch (method) {
            case "executeQuery" -> resultSet(cell);
            case "close" -> null;
            default -> null;
        });
    }

    private ResultSet resultSet(Object cell) {
        int[] cursor = {-1};
        return proxy(ResultSet.class, (method, arguments) -> switch (method) {
            case "next" -> ++cursor[0] == 0;
            case "getObject" -> cell;
            case "getString" -> String.valueOf(cell);
            case "getMetaData" -> proxy(java.sql.ResultSetMetaData.class,
                (metadataMethod, metadataArguments) -> switch (metadataMethod) {
                    case "getColumnCount" -> 1;
                    case "getColumnType" -> Types.BIGINT;
                    default -> null;
                });
            case "close" -> null;
            default -> null;
        });
    }

    @SuppressWarnings("unchecked")
    private static SqlResult<Object[]> rawQueryResult(SqlResult<?> result) {
        return (SqlResult<Object[]>) result;
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type, Invocation invocation) {
        return (T) Proxy.newProxyInstance(
            ExecutionPluginChainTest.class.getClassLoader(),
            new Class<?>[]{type},
            (proxy, method, arguments) -> invocation.invoke(
                method.getName(), arguments == null ? new Object[0] : arguments)
        );
    }

    @FunctionalInterface
    private interface Invocation {
        Object invoke(String method, Object[] arguments) throws Throwable;
    }
}
