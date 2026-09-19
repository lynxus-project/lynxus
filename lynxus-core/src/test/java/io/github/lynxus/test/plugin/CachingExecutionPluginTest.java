package io.github.lynxus.test.plugin;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import io.github.lynxus.JdbcAssembly;
import io.github.lynxus.api.BatchExecutionPlan;
import io.github.lynxus.api.ConnectionHandle;
import io.github.lynxus.api.ConnectionHandleFactory;
import io.github.lynxus.api.ExecutionInterceptor;
import io.github.lynxus.api.ExecutionOutcome;
import io.github.lynxus.api.ExecutionPlan;
import io.github.lynxus.api.JdbcExecutionState;
import io.github.lynxus.api.SqlExecutor;
import io.github.lynxus.api.SqlResult;
import io.github.lynxus.api.PageContext;
import io.github.lynxus.api.PageRequest;
import io.github.lynxus.plugin.CachingExecutionPlugin;
import io.github.lynxus.plugin.LimitOffsetPaginationDialect;
import io.github.lynxus.plugin.MemoryQueryCache;
import io.github.lynxus.plugin.PagingExecutionPlugin;

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

class CachingExecutionPluginTest {

    @AfterEach
    void clearPageContext() {
        PageContext.clear();
    }

    @Test
    void secondIdenticalSelectDoesNotCallJdbc() {
        AtomicInteger jdbcCalls = new AtomicInteger();
        SqlExecutor executor = cachingExecutor(countingFactory(jdbcCalls, 7L));
        ExecutionPlan plan = selectPlan("SELECT 1", 7L);

        SqlResult<Object[]> first = rawQueryResult(executor.execute(plan));
        SqlResult<Object[]> second = rawQueryResult(executor.execute(plan));

        assertEquals(1, jdbcCalls.get());
        assertEquals(7L, first.getQueryResults().getFirst()[0]);
        assertSame(first, second);
    }

    @Test
    void cacheHitReportsNotExecutedToOuterObserver() {
        AtomicInteger jdbcCalls = new AtomicInteger();
        AtomicReference<JdbcExecutionState> state = new AtomicReference<>();
        ExecutionInterceptor observer = new ExecutionInterceptor() {
            @Override
            public void afterSuccess(ExecutionOutcome outcome) {
                state.set(outcome.executionState());
            }
        };
        SqlExecutor executor = JdbcAssembly.sqlExecutor(
            countingFactory(jdbcCalls, 7L),
            List.of(observer),
            List.of(new CachingExecutionPlugin(new MemoryQueryCache())));
        ExecutionPlan plan = selectPlan("SELECT 1", 7L);

        executor.execute(plan);
        executor.execute(plan);

        assertEquals(1, jdbcCalls.get());
        assertEquals(JdbcExecutionState.NOT_EXECUTED, state.get());
    }

    @Test
    void writesAndCursorsAlwaysCallNext() {
        AtomicInteger jdbcCalls = new AtomicInteger();
        SqlExecutor executor = cachingExecutor(countingFactory(jdbcCalls, 7L));

        executor.execute(writePlan(ExecutionPlan.StatementType.UPDATE));
        executor.execute(writePlan(ExecutionPlan.StatementType.INSERT));
        executor.execute(new BatchExecutionPlan(
            "test.Mapper.batch",
            "INSERT INTO users(name) VALUES (?)",
            List.of(new Object[]{"a"}, new Object[]{"b"}),
            ExecutionPlan.SqlSource.GENERATED));
        executor.queryCursor(
            selectPlan("SELECT 1", 7L, resultSet -> resultSet.getObject(1)),
            cursor -> {
                assertTrue(cursor.next());
                return cursor.current();
            });

        assertEquals(4, jdbcCalls.get());
    }

    @Test
    void failedSelectIsNotCached() {
        AtomicInteger jdbcCalls = new AtomicInteger();
        SqlExecutor executor = cachingExecutor(() -> {
            jdbcCalls.incrementAndGet();
            throw new IllegalStateException("failed");
        });
        ExecutionPlan plan = selectPlan("SELECT 1", 7L);

        assertThrows(Exception.class, () -> executor.execute(plan));
        assertThrows(Exception.class, () -> executor.execute(plan));
        assertEquals(2, jdbcCalls.get());
    }

    @Test
    void pageRequestParticipatesInCacheIdentityRegardlessOfPluginOrder() {
        AtomicInteger jdbcCalls = new AtomicInteger();
        SqlExecutor executor = JdbcAssembly.sqlExecutor(
            countingFactory(jdbcCalls, 7L),
            List.of(),
            List.of(
                new CachingExecutionPlugin(new MemoryQueryCache()),
                new PagingExecutionPlugin(new LimitOffsetPaginationDialect())));
        ExecutionPlan plan = selectPlan("SELECT 1", 7L);

        PageContext.bind(new PageRequest(0, 1));
        executor.execute(plan);
        PageContext.bind(new PageRequest(1, 1));
        executor.execute(plan);

        assertEquals(2, jdbcCalls.get());
    }

    @Test
    void finalSqlParticipatesInCacheIdentity() {
        AtomicInteger jdbcCalls = new AtomicInteger();
        SqlExecutor executor = cachingExecutor(countingFactory(jdbcCalls, 7L));

        executor.execute(selectPlan("SELECT 1", 7L));
        executor.execute(selectPlan("SELECT 2", 7L));

        assertEquals(2, jdbcCalls.get());
    }

    private SqlExecutor cachingExecutor(ConnectionHandleFactory factory) {
        return JdbcAssembly.sqlExecutor(
            factory,
            List.of(),
            List.of(new CachingExecutionPlugin(new MemoryQueryCache())));
    }

    private ExecutionPlan selectPlan(String sql, Object parameter) {
        return selectPlan(sql, parameter, null);
    }

    private ExecutionPlan selectPlan(
            String sql, Object parameter, io.github.lynxus.api.RowMapper<?> rowMapper) {
        return new ExecutionPlan(
            "test.Mapper.find",
            sql,
            new Object[]{parameter},
            ExecutionPlan.StatementType.SELECT,
            ExecutionPlan.SqlSource.GENERATED,
            null,
            null,
            rowMapper,
            io.github.lynxus.api.StatementOptions.defaults(),
            null);
    }

    private ExecutionPlan writePlan(ExecutionPlan.StatementType type) {
        return new ExecutionPlan(
            "test.Mapper.write",
            "UPDATE users SET name = ?",
            new Object[]{"Alice"},
            type,
            ExecutionPlan.SqlSource.GENERATED);
    }

    private ConnectionHandleFactory countingFactory(AtomicInteger jdbcCalls, Object cell) {
        return () -> {
            jdbcCalls.incrementAndGet();
            return new ConnectionHandle() {
                @Override
                public Connection connection() {
                    return proxy(Connection.class, (method, arguments) -> {
                        if (method.equals("prepareStatement")) {
                            return statement(cell);
                        }
                        return null;
                    });
                }

                @Override
                public void close() {
                }
            };
        };
    }

    private PreparedStatement statement(Object cell) {
        return proxy(PreparedStatement.class, (method, arguments) -> switch (method) {
            case "executeQuery" -> resultSet(cell);
            case "executeUpdate" -> 1;
            case "addBatch", "setObject", "setQueryTimeout", "setFetchSize", "setMaxRows" -> null;
            case "executeBatch" -> new int[]{1, 1};
            case "close" -> null;
            default -> null;
        });
    }

    private ResultSet resultSet(Object cell) {
        int[] cursor = {-1};
        return proxy(ResultSet.class, (method, arguments) -> switch (method) {
            case "next" -> ++cursor[0] == 0;
            case "getObject" -> cell;
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
            CachingExecutionPluginTest.class.getClassLoader(),
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
