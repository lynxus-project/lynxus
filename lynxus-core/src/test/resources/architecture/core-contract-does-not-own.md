Core does not own:

- dependency injection or Mapper bean discovery;
- connection pooling, routing DataSource implementation, tenant or shard context;
- declarative transaction propagation, savepoints, distributed transactions, or transaction recovery;
- schema migration, session-scoped cache lifecycle or invalidation, lazy loading, nested object
  aggregation, or a MyBatis plugin runtime;
- framework pagination models or automatic count queries. Explicit `PaginationDialect` adapters may
  replace a SELECT plan at the `SqlExecutor` boundary.
