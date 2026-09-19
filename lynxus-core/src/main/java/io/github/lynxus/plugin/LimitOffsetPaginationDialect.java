package io.github.lynxus.plugin;

import io.github.lynxus.api.ExecutionPlan;
import io.github.lynxus.api.PageRequest;

import java.util.Objects;

/**
 * Appends {@code LIMIT ? OFFSET ?} for PostgreSQL and MySQL.
 */
public final class LimitOffsetPaginationDialect implements PaginationDialect {

    @Override
    public ExecutionPlan paginate(ExecutionPlan plan, PageRequest page) {
        Objects.requireNonNull(plan, "plan");
        Objects.requireNonNull(page, "page");
        Object[] original = plan.getParameters();
        Object[] parameters = new Object[original.length + 2];
        System.arraycopy(original, 0, parameters, 0, original.length);
        parameters[original.length] = page.limit();
        parameters[original.length + 1] = page.offset();
        return new ExecutionPlan(
            plan.getStatementId(),
            plan.getSql() + " LIMIT ? OFFSET ?",
            parameters,
            plan.getStatementType(),
            plan.getSourceType(),
            plan.getGeneratedKeyColumn(),
            plan.getParameterBinders(),
            plan.getRowMapper(),
            plan.getStatementOptions(),
            plan.getTypeRouting());
    }
}
