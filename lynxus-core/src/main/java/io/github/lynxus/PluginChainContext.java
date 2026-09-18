package io.github.lynxus;

import io.github.lynxus.api.JdbcExecutionState;

/** Internal JDBC-executed flag for the plugin chain. Not an application API. */
public final class PluginChainContext {

    private static final ThreadLocal<JdbcExecutionState> JDBC_STATE =
        ThreadLocal.withInitial(() -> JdbcExecutionState.NOT_EXECUTED);

    private PluginChainContext() {
    }

    static void reset() {
        JDBC_STATE.set(JdbcExecutionState.NOT_EXECUTED);
    }

    public static void markJdbcExecuted() {
        JDBC_STATE.set(JdbcExecutionState.EXECUTED);
    }

    static JdbcExecutionState jdbcState() {
        return JDBC_STATE.get();
    }

    static void clear() {
        JDBC_STATE.remove();
    }
}
