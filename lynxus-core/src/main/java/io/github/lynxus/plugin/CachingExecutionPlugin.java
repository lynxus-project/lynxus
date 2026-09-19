package io.github.lynxus.plugin;

import io.github.lynxus.api.ExecutionPlan;
import io.github.lynxus.api.ExecutionPlugin;
import io.github.lynxus.api.PageContext;
import io.github.lynxus.api.PageRequest;
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
        Optional<SqlResult<?>> cached = cache.get(cacheKey(plan), plan.getParameters());
        if (cached.isPresent()) {
            return cached.get();
        }
        SqlResult<?> result = next.execute(plan);
        cache.put(cacheKey(plan), plan.getParameters(), result);
        return result;
    }

    private String cacheKey(ExecutionPlan plan) {
        StringBuilder key = new StringBuilder(plan.getStatementId())
            .append('\u0000')
            .append(plan.getSql());
        PageRequest page = PageContext.current();
        if (page != null && plan.getStatementType() == ExecutionPlan.StatementType.SELECT) {
            key.append('\u0000').append(page.offset()).append('\u0000').append(page.limit());
        }
        return key.toString();
    }
}
