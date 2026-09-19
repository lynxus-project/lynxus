package io.github.lynxus.test.plugin;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import io.github.lynxus.JdbcAssembly;
import io.github.lynxus.api.ConnectionHandle;
import io.github.lynxus.api.ConnectionHandleFactory;
import io.github.lynxus.api.ExecutionPlan;
import io.github.lynxus.api.PageContext;
import io.github.lynxus.api.PageRequest;
import io.github.lynxus.api.SqlExecutor;
import io.github.lynxus.api.SqlResult;
import io.github.lynxus.plugin.LimitOffsetPaginationDialect;
import io.github.lynxus.plugin.PagingExecutionPlugin;

import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PagingExecutionPluginTest {

    @AfterEach
    void clearPage() {
        PageContext.clear();
    }

    @Test
    void rewritesSelectSqlAndAppendsLimitOffsetParameters() {
        List<String> preparedSql = new ArrayList<>();
        List<Object[]> bound = new ArrayList<>();
        PageContext.bind(new PageRequest(2, 1));
        rawQueryResult(pagingExecutor(factoryRecordingSql(preparedSql, bound, 9L))
            .execute(selectPlan("SELECT id FROM users ORDER BY id", 7L)));

        assertEquals(List.of("SELECT id FROM users ORDER BY id LIMIT ? OFFSET ?"), preparedSql);
        assertEquals(1, bound.size());
        assertArrayEquals(new Object[]{7L, 1, 2}, bound.getFirst());
    }

    @Test
    void unboundSelectAndWritesPassThroughWithoutLimit() {
        List<String> preparedSql = new ArrayList<>();
        pagingExecutor(factoryRecordingSql(preparedSql, new ArrayList<>(), 9L))
            .execute(selectPlan("SELECT id FROM users", 7L));
        PageContext.bind(new PageRequest(0, 1));
        pagingExecutor(factoryRecordingSql(preparedSql, new ArrayList<>(), 9L))
            .execute(new ExecutionPlan(
                "test.Mapper.write",
                "UPDATE users SET name = ?",
                new Object[]{"Alice"},
                ExecutionPlan.StatementType.UPDATE,
                ExecutionPlan.SqlSource.GENERATED));

        assertEquals(
            List.of("SELECT id FROM users", "UPDATE users SET name = ?"),
            preparedSql);
    }

    @Test
    void queryCursorDoesNotRewriteSql() {
        List<String> preparedSql = new ArrayList<>();
        PageContext.bind(new PageRequest(0, 1));
        pagingExecutor(factoryRecordingSql(preparedSql, new ArrayList<>(), "Alice"))
            .<String, String>queryCursor(
                new ExecutionPlan(
                    "test.Mapper.scan",
                    "SELECT name FROM users",
                    new Object[0],
                    ExecutionPlan.StatementType.SELECT,
                    ExecutionPlan.SqlSource.GENERATED,
                    null,
                    null,
                    resultSet -> resultSet.getObject(1),
                    io.github.lynxus.api.StatementOptions.defaults(),
                    null),
                cursor -> {
                    assertTrue(cursor.next());
                    return String.valueOf(cursor.current());
                });

        assertEquals(List.of("SELECT name FROM users"), preparedSql);
    }

    @Test
    void rejectsNegativePageRequestBeforeNext() {
        AtomicInteger jdbcCalls = new AtomicInteger();
        assertThrows(IllegalArgumentException.class, () -> new PageRequest(-1, 1));
        assertThrows(IllegalArgumentException.class, () -> new PageRequest(0, -1));
        assertEquals(0, jdbcCalls.get());
    }

    @Test
    void dialectKeepsStatementIdentityBindersAndRowMapper() {
        io.github.lynxus.api.RowMapper<?> mapper = resultSet -> resultSet.getObject(1);
        ExecutionPlan original = new ExecutionPlan(
            "test.Mapper.find",
            "SELECT id FROM users",
            new Object[]{7L},
            ExecutionPlan.StatementType.SELECT,
            ExecutionPlan.SqlSource.GENERATED,
            null,
            null,
            mapper,
            io.github.lynxus.api.StatementOptions.defaults(),
            null);
        ExecutionPlan paged = new LimitOffsetPaginationDialect().paginate(original, new PageRequest(0, 1));

        assertEquals(original.getStatementId(), paged.getStatementId());
        assertEquals(original.getStatementType(), paged.getStatementType());
        assertEquals(original.getRowMapper(), paged.getRowMapper());
        assertEquals(original.getParameterBinders(), paged.getParameterBinders());
    }

    private SqlExecutor pagingExecutor(ConnectionHandleFactory factory) {
        return JdbcAssembly.sqlExecutor(
            factory,
            List.of(),
            List.of(new PagingExecutionPlugin(new LimitOffsetPaginationDialect())));
    }

    private ExecutionPlan selectPlan(String sql, Object parameter) {
        return new ExecutionPlan(
            "test.Mapper.find",
            sql,
            new Object[]{parameter},
            ExecutionPlan.StatementType.SELECT,
            ExecutionPlan.SqlSource.GENERATED);
    }

    private ConnectionHandleFactory factoryRecordingSql(
            List<String> preparedSql, List<Object[]> bound, Object cell) {
        return () -> new ConnectionHandle() {
            @Override
            public Connection connection() {
                return proxy(Connection.class, (method, arguments) -> {
                    if (method.equals("prepareStatement")) {
                        preparedSql.add((String) arguments[0]);
                        return statement(bound, cell);
                    }
                    return null;
                });
            }

            @Override
            public void close() {
            }
        };
    }

    private PreparedStatement statement(List<Object[]> bound, Object cell) {
        List<Object> values = new ArrayList<>();
        return proxy(PreparedStatement.class, (method, arguments) -> switch (method) {
            case "setObject" -> {
                int index = (Integer) arguments[0];
                while (values.size() < index) {
                    values.add(null);
                }
                values.set(index - 1, arguments[1]);
                yield null;
            }
            case "executeQuery" -> {
                bound.add(values.toArray());
                yield resultSet(cell);
            }
            case "executeUpdate" -> 1;
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
            PagingExecutionPluginTest.class.getClassLoader(),
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
