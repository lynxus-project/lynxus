package io.github.lynxus.test;

import org.junit.jupiter.api.Test;
import io.github.lynxus.JdbcAssembly;
import io.github.lynxus.Lynxus;
import io.github.lynxus.api.ConfigurationException;
import io.github.lynxus.api.ConnectionHandleFactory;
import io.github.lynxus.api.ExecutionInterceptor;
import io.github.lynxus.api.ExecutionOutcome;
import io.github.lynxus.api.ExecutionPlan;
import io.github.lynxus.api.SqlExecutionException;
import io.github.lynxus.api.SqlResult;
import io.github.lynxus.api.TransactionException;
import io.github.lynxus.transaction.SimpleTransactionDomainGuard;

import javax.sql.DataSource;
import java.io.PrintWriter;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLFeatureNotSupportedException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LynxusAssemblyTest {

    @Test
    void assemblesExplicitExecutorRolesWithOrderedInterceptors() {
        TrackingDataSource dataSource = new TrackingDataSource();
        List<String> events = new ArrayList<>();
        ExecutionInterceptor first = interceptor("first", events);
        ExecutionInterceptor second = interceptor("second", events);

        JdbcAssembly assembly = Lynxus.jdbc(dataSource)
            .interceptors(List.of(first, second))
            .build();

        SqlResult<Object[]> result = rawQueryResult(
            assembly.sqlExecutor().execute(selectPlan("test.Mapper.find")));

        assertEquals(7L, result.getQueryResults().getFirst()[0]);
        assertEquals(List.of(
            "first.before", "second.before",
            "second.success", "first.success"
        ), events);
        assertFalse(
            assembly.sqlExecutor() instanceof io.github.lynxus.jdbc.JdbcSqlExecutor,
            "observational interceptors must wrap SqlExecutor outside JdbcSqlExecutor");
        assertInstanceOf(
            io.github.lynxus.transaction.SimpleTransactionalExecutor.class,
            assembly.transactionalExecutor()
        );
    }

    @Test
    void executorJoinsTransactionFromTheSameAssembly() {
        TrackingDataSource dataSource = new TrackingDataSource();
        JdbcAssembly assembly = Lynxus.jdbc(dataSource).build();

        assembly.transactionalExecutor().execute(() -> {
            assembly.sqlExecutor().execute(selectPlan("test.Mapper.first"));
            assembly.sqlExecutor().execute(selectPlan("test.Mapper.second"));
            return null;
        });

        assertEquals(1, dataSource.connectionCount.get());
        assertEquals(1, dataSource.commitCount.get());
        assertEquals(1, dataSource.closeCount.get());
    }

    @Test
    void sharedGuardRejectsCrossDomainExecutionBeforeSecondConnection() {
        TrackingDataSource usersDataSource = new TrackingDataSource();
        TrackingDataSource ordersDataSource = new TrackingDataSource();
        SimpleTransactionDomainGuard guard = new SimpleTransactionDomainGuard();
        JdbcAssembly users = Lynxus.jdbc(usersDataSource)
            .domain("users")
            .domainGuard(guard)
            .build();
        JdbcAssembly orders = Lynxus.jdbc(ordersDataSource)
            .domain("orders")
            .domainGuard(guard)
            .build();

        SqlExecutionException failure = assertThrows(SqlExecutionException.class, () ->
            users.transactionalExecutor().execute(() -> {
                users.sqlExecutor().execute(selectPlan("users.Mapper.find"));
                orders.sqlExecutor().execute(selectPlan("orders.Mapper.find"));
                return null;
            })
        );

        TransactionException cause = assertInstanceOf(TransactionException.class, failure.getCause());
        assertEquals(TransactionException.Type.DOMAIN_MISMATCH, cause.getType());
        assertEquals(1, usersDataSource.connectionCount.get());
        assertEquals(0, ordersDataSource.connectionCount.get());
    }

    @Test
    void rejectsInvalidAssemblyConfiguration() {
        TrackingDataSource dataSource = new TrackingDataSource();
        ExecutionInterceptor interceptor = new ExecutionInterceptor() {
        };

        assertConfigurationFailure("dataSource", () -> Lynxus.jdbc(null));
        assertConfigurationFailure("domain", () -> Lynxus.jdbc(dataSource).domain(" "));
        assertConfigurationFailure("interceptors", () ->
            Lynxus.jdbc(dataSource).interceptors(null));
        assertConfigurationFailure("interceptors[1]", () ->
            Lynxus.jdbc(dataSource).interceptors(Arrays.asList(interceptor, null)));
        assertConfigurationFailure("duplicate", () ->
            Lynxus.jdbc(dataSource).interceptors(List.of(interceptor, interceptor)));
    }

    @Test
    void assemblesSqlExecutorFromHostConnectionHandleFactory() {
        ConnectionHandleFactory connectionHandleFactory = () -> null;

        assertInstanceOf(
            io.github.lynxus.jdbc.JdbcSqlExecutor.class,
            JdbcAssembly.sqlExecutor(connectionHandleFactory, List.of())
        );
    }

    private void assertConfigurationFailure(String messagePart, Runnable action) {
        ConfigurationException failure = assertThrows(ConfigurationException.class, action::run);
        assertTrue(failure.getMessage().contains(messagePart), failure.getMessage());
    }

    private ExecutionPlan selectPlan(String statementId) {
        return new ExecutionPlan(
            statementId,
            "SELECT 7",
            new Object[0],
            ExecutionPlan.StatementType.SELECT,
            ExecutionPlan.SqlSource.GENERATED
        );
    }

    private ExecutionInterceptor interceptor(String name, List<String> events) {
        return new ExecutionInterceptor() {
            @Override
            public void beforeExecution(ExecutionPlan plan) {
                events.add(name + ".before");
            }

            @Override
            public void afterSuccess(ExecutionOutcome outcome) {
                events.add(name + ".success");
            }
        };
    }

    private static final class TrackingDataSource implements DataSource {

        private final AtomicInteger connectionCount = new AtomicInteger();
        private final AtomicInteger commitCount = new AtomicInteger();
        private final AtomicInteger closeCount = new AtomicInteger();

        @Override
        public Connection getConnection() {
            connectionCount.incrementAndGet();
            boolean[] autoCommit = {true};
            return proxy(Connection.class, (method, arguments) -> switch (method) {
                case "getAutoCommit" -> autoCommit[0];
                case "setAutoCommit" -> {
                    autoCommit[0] = (boolean) arguments[0];
                    yield null;
                }
                case "prepareStatement" -> statement();
                case "commit" -> {
                    commitCount.incrementAndGet();
                    yield null;
                }
                case "rollback" -> null;
                case "close" -> {
                    closeCount.incrementAndGet();
                    yield null;
                }
                case "isClosed" -> false;
                default -> null;
            });
        }

        private PreparedStatement statement() {
            return proxy(PreparedStatement.class, (method, arguments) -> switch (method) {
                case "executeQuery" -> resultSet();
                case "close" -> null;
                default -> null;
            });
        }

        private ResultSet resultSet() {
            int[] cursor = {-1};
            return proxy(ResultSet.class, (method, arguments) -> switch (method) {
                case "next" -> ++cursor[0] == 0;
                case "getObject" -> 7L;
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

        @Override
        public Connection getConnection(String username, String password) {
            return getConnection();
        }

        @Override public PrintWriter getLogWriter() { return null; }
        @Override public void setLogWriter(PrintWriter out) { }
        @Override public void setLoginTimeout(int seconds) { }
        @Override public int getLoginTimeout() { return 0; }
        @Override public Logger getParentLogger() throws SQLFeatureNotSupportedException {
            throw new SQLFeatureNotSupportedException();
        }
        @Override public <T> T unwrap(Class<T> iface) { throw new UnsupportedOperationException(); }
        @Override public boolean isWrapperFor(Class<?> iface) { return false; }
    }

    @SuppressWarnings("unchecked")
    private static SqlResult<Object[]> rawQueryResult(SqlResult<?> result) {
        return (SqlResult<Object[]>) result;
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type, Invocation invocation) {
        return (T) Proxy.newProxyInstance(
            LynxusAssemblyTest.class.getClassLoader(),
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
