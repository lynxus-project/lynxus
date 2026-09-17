package io.github.lynxus;

import io.github.lynxus.api.CursorCallback;
import io.github.lynxus.api.ExecutionPlan;
import io.github.lynxus.api.ParameterBinder;
import io.github.lynxus.api.SqlExecutor;
import io.github.lynxus.api.SqlResult;

import java.util.Arrays;
import java.util.Objects;

final class GuardedSqlExecutor implements SqlExecutor {

    private final ExecutionPlan original;
    private final SqlExecutor next;

    GuardedSqlExecutor(ExecutionPlan original, SqlExecutor next) {
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
        if (!sameBinders(original.getParameterBinders(), candidate.getParameterBinders())) {
            throw new IllegalArgumentException("plugin cannot replace parameter binders");
        }
        if (original.getRowMapper() != candidate.getRowMapper()) {
            throw new IllegalArgumentException("plugin cannot replace row mapper");
        }
    }

    private static boolean sameBinders(ParameterBinder<?>[] left, ParameterBinder<?>[] right) {
        return Arrays.equals(left, right);
    }
}
