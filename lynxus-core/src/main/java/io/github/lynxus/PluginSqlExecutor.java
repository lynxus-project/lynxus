package io.github.lynxus;

import io.github.lynxus.api.CursorCallback;
import io.github.lynxus.api.ExecutionPlan;
import io.github.lynxus.api.ExecutionPlugin;
import io.github.lynxus.api.SqlExecutor;
import io.github.lynxus.api.SqlResult;

import java.util.Objects;

final class PluginSqlExecutor implements SqlExecutor {

    private final ExecutionPlugin plugin;
    private final SqlExecutor next;

    PluginSqlExecutor(ExecutionPlugin plugin, SqlExecutor next) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.next = Objects.requireNonNull(next, "next");
    }

    @Override
    public SqlResult<?> execute(ExecutionPlan plan) {
        return plugin.intercept(plan, new PlanReplacementSqlExecutor(plan, next));
    }

    @Override
    public <T, R> R queryCursor(ExecutionPlan plan, CursorCallback<T, R> callback) {
        return plugin.interceptCursor(plan, callback, new PlanReplacementSqlExecutor(plan, next));
    }
}
