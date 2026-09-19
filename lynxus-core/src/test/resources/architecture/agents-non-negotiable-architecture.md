## Non-Negotiable Architecture

- Generated Mappers depend on `SqlExecutor`, not on a session, proxy, container, or compiler implementation.
- `JdbcSqlExecutor` owns one fixed JDBC lifecycle.
- Spring owns IoC, transaction participation, and DataSource binding; it must not duplicate JDBC execution.
- One Mapper belongs to one DataSource domain.
- Runtime artifacts must not contain the annotation processor, FreeMarker, or another template engine.
- Mapper XML and annotation scripts are compiled; Lynxus does not interpret XML or OGNL at runtime.
- Do not add `SqlSession`, runtime Mapper proxies, ORM caches, lazy loading, automatic count queries, framework `Page<T>`, or a general SQL-rewrite plugin chain.
