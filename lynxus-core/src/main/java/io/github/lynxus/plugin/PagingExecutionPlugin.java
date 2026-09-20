package io.github.lynxus.plugin;

import io.github.lynxus.api.ExecutionPlan;
import io.github.lynxus.api.ExecutionPlugin;
import io.github.lynxus.api.PageContext;
import io.github.lynxus.api.PageRequest;
import io.github.lynxus.api.SqlExecutor;
import io.github.lynxus.api.SqlResult;

import java.util.Objects;

/**
 * Replaces a SELECT plan with a dialect paged plan when a {@link PageRequest} is bound.
 * Non-SELECT statements and unbound calls pass through. Cursors pass through.
 */
public final class PagingExecutionPlugin implements ExecutionPlugin {

    private final PaginationDialect dialect;

    public PagingExecutionPlugin(PaginationDialect dialect) {
        this.dialect = Objects.requireNonNull(dialect, "dialect");
    }

    @Override
    public SqlResult<?> intercept(ExecutionPlan plan, SqlExecutor next) {
        PageRequest page = PageContext.current();
        if (page == null || plan.getStatementType() != ExecutionPlan.StatementType.SELECT) {
            return next.execute(plan);
        }
        return next.execute(dialect.paginate(plan, page));
    }
}
