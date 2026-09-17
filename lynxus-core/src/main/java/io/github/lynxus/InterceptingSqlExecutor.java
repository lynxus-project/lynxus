package io.github.lynxus;

import io.github.lynxus.api.CursorCallback;
import io.github.lynxus.api.ExecutionInterceptor;
import io.github.lynxus.api.ExecutionOutcome;
import io.github.lynxus.api.ExecutionPhase;
import io.github.lynxus.api.ExecutionPlan;
import io.github.lynxus.api.JdbcExecutionState;
import io.github.lynxus.api.SqlExecutionException;
import io.github.lynxus.api.SqlExecutor;
import io.github.lynxus.api.SqlResult;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Observational interceptor adapter around {@link SqlExecutor}. Innermost {@code next} is JDBC.
 */
final class InterceptingSqlExecutor implements SqlExecutor {

    private static final System.Logger LOGGER = System.getLogger(InterceptingSqlExecutor.class.getName());

    private final SqlExecutor next;
    private final List<ExecutionInterceptor> interceptors;

    private InterceptingSqlExecutor(SqlExecutor next, List<ExecutionInterceptor> interceptors) {
        this.next = next;
        this.interceptors = interceptors;
    }

    static SqlExecutor wrap(SqlExecutor next, List<ExecutionInterceptor> interceptors) {
        Objects.requireNonNull(next, "next");
        Objects.requireNonNull(interceptors, "interceptors");
        if (interceptors.isEmpty()) {
            return next;
        }
        return new InterceptingSqlExecutor(next, List.copyOf(interceptors));
    }

    @Override
    public SqlResult<?> execute(ExecutionPlan plan) {
        return invoke(plan, () -> next.execute(plan));
    }

    @Override
    public <T, R> R queryCursor(ExecutionPlan plan, CursorCallback<T, R> callback) {
        return invoke(plan, () -> next.queryCursor(plan, callback));
    }

    private <T> T invoke(ExecutionPlan plan, Execution<T> execution) {
        Objects.requireNonNull(plan, "plan");
        long startedAt = System.nanoTime();
        List<ExecutionInterceptor> entered = new ArrayList<>(interceptors.size());
        try {
            invokeBefore(plan, entered);
            T result = execution.run();
            invokeSuccess(entered, successOutcome(plan, startedAt, result));
            return result;
        } catch (Throwable failure) {
            ExecutionOutcome outcome = failureOutcome(plan, startedAt, failure, entered.size() < interceptors.size());
            invokeTerminal(entered, outcome);
            throwIfFailed(outcome);
            throw new IllegalStateException("unreachable");
        }
    }

    private void invokeBefore(ExecutionPlan plan, List<ExecutionInterceptor> entered) {
        for (ExecutionInterceptor interceptor : interceptors) {
            interceptor.beforeExecution(plan);
            entered.add(interceptor);
        }
    }

    private void invokeTerminal(List<ExecutionInterceptor> entered, ExecutionOutcome outcome) {
        if (outcome.failed()) {
            invokeFailure(entered, outcome);
        } else {
            invokeSuccess(entered, outcome);
        }
    }

    private void invokeSuccess(List<ExecutionInterceptor> entered, ExecutionOutcome outcome) {
        for (int index = entered.size() - 1; index >= 0; index--) {
            ExecutionInterceptor interceptor = entered.get(index);
            try {
                interceptor.afterSuccess(outcome);
            } catch (RuntimeException callbackFailure) {
                logTerminalFailure(interceptor, outcome, callbackFailure);
            }
        }
    }

    private void invokeFailure(List<ExecutionInterceptor> entered, ExecutionOutcome outcome) {
        for (int index = entered.size() - 1; index >= 0; index--) {
            ExecutionInterceptor interceptor = entered.get(index);
            try {
                interceptor.afterFailure(outcome);
            } catch (RuntimeException callbackFailure) {
                logTerminalFailure(interceptor, outcome, callbackFailure);
            }
        }
    }

    private ExecutionOutcome successOutcome(ExecutionPlan plan, long startedAt, Object result) {
        int affectedRows = 0;
        int resultCount = 0;
        if (result instanceof SqlResult<?> sqlResult) {
            affectedRows = sqlResult.isQuery() ? 0 : sqlResult.getUpdateCount();
            resultCount = sqlResult.getQueryResults() == null ? 0 : sqlResult.getQueryResults().size();
        }
        return ExecutionOutcome.success(
            plan, JdbcExecutionState.EXECUTED, elapsed(startedAt), affectedRows, resultCount);
    }

    private ExecutionOutcome failureOutcome(
            ExecutionPlan plan, long startedAt, Throwable failure, boolean beforeFailed) {
        long durationNanos = elapsed(startedAt);
        if (beforeFailed) {
            if (failure instanceof Error) {
                return ExecutionOutcome.failure(
                    plan, JdbcExecutionState.NOT_EXECUTED, durationNanos, 0, 0, failure);
            }
            return ExecutionOutcome.failure(
                plan,
                JdbcExecutionState.NOT_EXECUTED,
                durationNanos,
                0,
                0,
                new SqlExecutionException(
                    plan, ExecutionPhase.PREPARATION, JdbcExecutionState.NOT_EXECUTED, failure));
        }
        if (failure instanceof SqlExecutionException sqlFailure) {
            return ExecutionOutcome.failure(
                plan, sqlFailure.getExecutionState(), durationNanos, 0, 0, sqlFailure);
        }
        return ExecutionOutcome.failure(
            plan, JdbcExecutionState.OUTCOME_UNKNOWN, durationNanos, 0, 0, failure);
    }

    private void throwIfFailed(ExecutionOutcome outcome) {
        Throwable finalFailure = outcome.failure();
        if (finalFailure instanceof Error error) {
            throw error;
        }
        if (finalFailure != null) {
            throw (RuntimeException) finalFailure;
        }
    }

    private void logTerminalFailure(
            ExecutionInterceptor interceptor, ExecutionOutcome outcome, RuntimeException failure) {
        LOGGER.log(
            System.Logger.Level.WARNING,
            "Lynxus interceptor terminal callback failed [interceptor="
                + interceptor.getClass().getName()
                + ", statementId=" + outcome.plan().getStatementId()
                + ", executionState=" + outcome.executionState() + ']',
            failure
        );
    }

    private static long elapsed(long startedAt) {
        return Math.max(0L, System.nanoTime() - startedAt);
    }

    @FunctionalInterface
    private interface Execution<T> {
        T run() throws Exception;
    }
}
