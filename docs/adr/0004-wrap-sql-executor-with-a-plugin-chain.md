# Wrap SqlExecutor with a plugin chain

Executor-level policy (observation, cache, pagination) is a chain of adapters around `SqlExecutor`. `JdbcSqlExecutor` owns only the JDBC lifecycle. Generated Mappers keep calling `SqlExecutor`; they do not learn about plugins. Mapper dynamic SQL, `BoundSqlBuilder`, and method-level providers, binders, and row mappers stay outside this chain.

The chain is not a pluggable JDBC phase pipeline and not a MyBatis `proceed()` wrap of statement, parameter, or result handlers. Association loading remains generated Mapper code, not a plugin.

Settled contract: a plugin sees an immutable plan and the remaining `SqlExecutor` as `next`. It may call `next` unchanged, call `next` with a new immutable plan, or return a result without calling `next`. Short-circuit skips inner plugins and JDBC. Observational `ExecutionInterceptor` remains as an adapter on the chain. Its `ExecutionOutcome` is synthesized from the `next` call: cache hits report `NOT_EXECUTED`. Registration order is outer to inner and preserves today's before/after sequencing. `queryCursor` uses the same chain.
