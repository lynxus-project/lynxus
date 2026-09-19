Core does not own:

- dependency injection or Mapper bean discovery;
- connection pooling, routing DataSource implementation, tenant or shard context;
- declarative transaction propagation, savepoints, distributed transactions, or transaction recovery;
- schema migration, caching, lazy loading, nested object aggregation, or a MyBatis plugin runtime;
- SQL-dialect pagination generation. Pagination is expressed as dynamic SQL in the Mapper or provider.
