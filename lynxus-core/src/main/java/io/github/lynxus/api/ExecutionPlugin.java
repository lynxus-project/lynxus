package io.github.lynxus.api;

/**
 * Executor-level plugin around {@link SqlExecutor}. A plugin may call {@code next} unchanged,
 * call {@code next} with a new immutable plan, or return a result without calling {@code next}.
 * It must not intercept JDBC prepare, bind, or mapping internals.
 */
@FunctionalInterface
public interface ExecutionPlugin {

    SqlResult<?> intercept(ExecutionPlan plan, SqlExecutor next);

    /**
     * Cursor executions use the same chain. Plugins that do not handle cursors must pass through.
     */
    default <T, R> R interceptCursor(
            ExecutionPlan plan, CursorCallback<T, R> callback, SqlExecutor next) {
        return next.queryCursor(plan, callback);
    }
}
