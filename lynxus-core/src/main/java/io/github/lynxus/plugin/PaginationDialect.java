package io.github.lynxus.plugin;

import io.github.lynxus.api.ExecutionPlan;
import io.github.lynxus.api.PageRequest;

/**
 * Rewrites a SELECT plan to include paging. Implementations must keep statement identity,
 * binders, and row mapper, and may change SQL text and parameters only.
 */
public interface PaginationDialect {

    ExecutionPlan paginate(ExecutionPlan plan, PageRequest page);
}
