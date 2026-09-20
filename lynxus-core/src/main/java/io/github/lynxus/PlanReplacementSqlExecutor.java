package io.github.lynxus;

import io.github.lynxus.api.CursorCallback;
import io.github.lynxus.api.ExecutionPlan;
import io.github.lynxus.api.ParameterBinder;
import io.github.lynxus.api.SqlExecutor;
import io.github.lynxus.api.SqlResult;

import java.util.Arrays;
import java.util.Objects;

final class PlanReplacementSqlExecutor implements SqlExecutor {

    private final ExecutionPlan original;
    private final SqlExecutor next;

    PlanReplacementSqlExecutor(ExecutionPlan original, SqlExecutor next) {
        this.original = Objects.requireNonNull(original, "original");
        this.next = Objects.requireNonNull(next, "next");
    }

    @Override
    public SqlResult<?> execute(ExecutionPlan plan) {
        validateReplacement(original, plan);
        return next.execute(plan);
    }

    @Override
    public <T, R> R queryCursor(ExecutionPlan plan, CursorCallback<T, R> callback) {
        validateReplacement(original, plan);
        return next.queryCursor(plan, callback);
    }

    static void validateReplacement(ExecutionPlan original, ExecutionPlan candidate) {
        Objects.requireNonNull(candidate, "plan");
        if (original == candidate) {
            return;
        }
        if (!original.getStatementId().equals(candidate.getStatementId())) {
            throw new IllegalArgumentException("plugin cannot replace statementId");
        }
        if (original.getStatementType() != candidate.getStatementType()) {
            throw new IllegalArgumentException("plugin cannot replace statement type");
        }
        if (original.getSourceType() != candidate.getSourceType()) {
            throw new IllegalArgumentException("plugin cannot replace SQL source");
        }
        if (!Objects.equals(original.getGeneratedKeyColumn(), candidate.getGeneratedKeyColumn())) {
            throw new IllegalArgumentException("plugin cannot replace generated-key configuration");
        }
        if (!sameBinders(original.getParameterBinders(), candidate.getParameterBinders())) {
            throw new IllegalArgumentException("plugin cannot replace parameter binders");
        }
        if (original.getRowMapper() != candidate.getRowMapper()) {
            throw new IllegalArgumentException("plugin cannot replace row mapper");
        }
        if (!sameTypeRouting(original.getTypeRouting(), candidate.getTypeRouting())) {
            throw new IllegalArgumentException("plugin cannot replace type routing");
        }
    }

    private static boolean sameBinders(ParameterBinder<?>[] left, ParameterBinder<?>[] right) {
        return Arrays.equals(left, right);
    }

    private static boolean sameTypeRouting(
            ExecutionPlan.TypeRouting left, ExecutionPlan.TypeRouting right) {
        if (left == right) {
            return true;
        }
        if (left == null || right == null) {
            return false;
        }
        return Arrays.equals(left.parameterTypes(), right.parameterTypes())
            && Arrays.equals(left.parameterJdbcTypes(), right.parameterJdbcTypes())
            && Arrays.equals(left.resultTypes(), right.resultTypes())
            && Arrays.equals(left.resultColumnLabels(), right.resultColumnLabels());
    }
}
