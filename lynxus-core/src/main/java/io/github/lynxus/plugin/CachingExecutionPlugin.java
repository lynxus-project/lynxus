package io.github.lynxus.plugin;

import io.github.lynxus.api.ExecutionPlan;
import io.github.lynxus.api.ExecutionPlugin;
import io.github.lynxus.api.QueryCache;
import io.github.lynxus.api.SqlExecutor;
import io.github.lynxus.api.SqlResult;

import java.util.Objects;
import java.util.Optional;

/**
 * Short-circuits identical SELECT {@code execute} calls through {@link QueryCache}.
 * Writes, batches, and cursors pass through. Failures are not stored. Writes do not invalidate.
 */
public final class CachingExecutionPlugin implements ExecutionPlugin {

    private final QueryCache cache;

    public CachingExecutionPlugin(QueryCache cache) {
        this.cache = Objects.requireNonNull(cache, "cache");
    }

    @Override
    public SqlResult<?> intercept(ExecutionPlan plan, SqlExecutor next) {
        if (plan.getStatementType() != ExecutionPlan.StatementType.SELECT) {
            return next.execute(plan);
        }
        Optional<SqlResult<?>> cached = cache.get(plan.getStatementId(), plan.getParameters());
        if (cached.isPresent()) {
            return cached.get();
        }
        SqlResult<?> result = next.execute(plan);
        cache.put(plan.getStatementId(), plan.getParameters(), result);
        return result;
    }
}
