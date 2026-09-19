package io.github.lynxus;

import io.github.lynxus.api.CursorCallback;
import io.github.lynxus.api.ExecutionPlan;
import io.github.lynxus.api.SqlExecutor;
import io.github.lynxus.api.SqlResult;

import java.util.Objects;

final class PluginChainHead implements SqlExecutor {

    private final SqlExecutor next;

    PluginChainHead(SqlExecutor next) {
        this.next = Objects.requireNonNull(next, "next");
    }

    @Override
    public SqlResult<?> execute(ExecutionPlan plan) {
        PluginChainContext.reset();
        try {
            return next.execute(plan);
        } finally {
            PluginChainContext.clear();
        }
    }

    @Override
    public <T, R> R queryCursor(ExecutionPlan plan, CursorCallback<T, R> callback) {
        PluginChainContext.reset();
        try {
            return next.queryCursor(plan, callback);
        } finally {
            PluginChainContext.clear();
        }
    }
}
