package io.github.lynxus;

import io.github.lynxus.api.CursorCallback;
import io.github.lynxus.api.ExecutionPlan;
import io.github.lynxus.api.SqlExecutor;
import io.github.lynxus.api.SqlResult;

import java.util.Objects;

final class JdbcInvocationProbe implements SqlExecutor {

    private final SqlExecutor jdbc;

    JdbcInvocationProbe(SqlExecutor jdbc) {
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc");
    }

    @Override
    public SqlResult<?> execute(ExecutionPlan plan) {
        PluginChainContext.markJdbcExecuted();
        return jdbc.execute(plan);
    }

    @Override
    public <T, R> R queryCursor(ExecutionPlan plan, CursorCallback<T, R> callback) {
        PluginChainContext.markJdbcExecuted();
        return jdbc.queryCursor(plan, callback);
    }
}
