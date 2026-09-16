# Extension Contracts

Lynxus keeps generated Mapper code as the default. Extensions are narrow, explicit escape hatches for cases that cannot remain fully generated.

For the top-level mental model and a decision diagram, start with [Choosing a Value or Row Mapping](../user/core/mapping.md). This document owns the precise validation and lifecycle contracts.

## Guarantee Levels

| Path | Compile-time guarantees | Runtime work |
| --- | --- | --- |
| Generated annotations/XML | Strongest: SQL source, supported expressions, parameter order, and built-in mapping are generated | JDBC execution only |
| Typed extension | Extension class, generic compatibility, visibility, constructor, and invocation are compile-time-bound | Explicit provider, binder, mapper, or interceptor code runs |
| Raw JDBC | Outside Lynxus generation guarantees | Application owns SQL, binding, mapping, resources, and diagnostics |

## Concurrency Contract

Generated Mapper implementations and `JdbcSqlExecutor` are designed for concurrent reuse. Each Mapper call builds its own immutable execution plan, while simple transaction state and Spring transaction-bound connections remain isolated by thread.

Generated code keeps one Provider, Binder, and RowMapper instance per Mapper instance. `JdbcSqlExecutor` also reuses its configured Interceptor instances, and Spring normally supplies those interceptors as singleton beans. Therefore every Provider, Binder, RowMapper, and Interceptor implementation must be stateless, thread-safe, or protect its mutable state with external synchronization. Lynxus does not clone extension instances per call and provides no stateful-extension factory contract.

## Spring Boot Version Policy

The Starter targets Java 21 and verifies each supported Spring Boot line with
both an external consumer and the runtime Starter suite. The repository's
dependency-management baseline is Spring Boot 3.1.5.

| Spring Boot line | Verified anchor | Evidence | Claim boundary |
| --- | --- | --- | --- |
| 3.1.x | 3.1.5 | Starter runtime suite and external consumer | Other 3.1 patches are expected to remain compatible but are not individually tested here |
| 3.5.x | 3.5.16 | Starter runtime suite and external consumer | Other 3.5 patches are unverified |
| 4.1.x | 4.1.1 | Starter runtime suite and external consumer | Other 4.1 patches are unverified |

An unlisted Spring Boot minor line is unverified until it passes the same
consumer and runtime gates. The Starter does not claim compatibility with every
3.x or 4.x release merely because the API compiles.

## Spring Package-to-DataSource Binding

Spring registration uses explicit package bindings rather than type-only selection:

```yaml
lynxus:
  mapper-bindings:
    - package-name: com.example.user.mapper
      data-source: usersDataSource
    - package-name: com.example.order.mapper
      data-source: ordersDataSource
```

- Every package rule names exactly one Spring `DataSource` bean.
- The named bean may be a physical pool or a routing DataSource proxy.
- Starter uses `SpringConnectionHandleFactory` and core `JdbcAssembly` to create the executor.
- Duplicate, parent, and child package rules cannot overlap.
- Every Mapper interface is registered once and belongs to one DataSource domain. Applications with multiple DataSources use disjoint Mapper package bindings.
- Bindings remain explicit when only one DataSource exists; the starter does not infer a default DataSource or Mapper scan package.
- Package bindings select a DataSource domain only. They do not select a JDBC mapping family; Core's fixed, database-independent `TypeHandlerManager` routes standard values for every domain.
- The Starter does not inspect database product metadata, maintain package mapping registries, or load database-specific mapping artifacts.
- Generated Mapper implementations remain plain Java classes without Spring component or injection annotations.
- The processor writes one metadata resource per generated Mapper under
  `META-INF/lynxus/mappers/<generated-implementation-class>.properties`.
- The Starter loads these resources from the classpath and discovers only
  generated classes with valid processor metadata.
- Binding happens during application startup. Mapper invocation still calls its final executor field directly and performs no package or bean lookup.
- Each Spring transaction boundary must use the `PlatformTransactionManager` associated with the same DataSource as the selected executor.

## Host Lifecycle Ownership

A host integration may provide a `ConnectionHandleFactory` that participates in host-bound connections and may own transaction commit or rollback. These are the only lifecycle responsibilities replaced by the host.

`JdbcSqlExecutor` always owns statement preparation, statement options, parameter binding, SQL execution, generated-key handling, result reading, result mapping, cursor deactivation, executor-owned cleanup, final outcome formation, and terminal interceptor delivery. Spring Starter assembles and reuses that core executor; it must not copy, wrap into a second phase lifecycle, or reimplement those JDBC operations.

Closing a host-aware `ConnectionHandle` releases one executor participation. It does not claim that the physical connection was closed or that the host transaction committed or rolled back. Transaction completion remains outside `ExecutionOutcome`.

The boundary is verified by the core JDBC, cursor, and standalone transaction tests plus the Spring Starter integration suite:

```bash
mvn -pl lynxus-core -Dtest=JdbcSqlExecutorTest,JdbcCursorExecutionTest,SimpleTransactionTest test
mvn -pl lynxus-spring-boot-starter -am test
rg -n 'prepareStatement|executeQuery|executeUpdate|getGeneratedKeys' lynxus-spring-boot-starter/src/main/java
```

## Standalone Assembly And Transactions

Create one immutable `JdbcAssembly` per DataSource domain:

```java
JdbcAssembly assembly = Lynxus.jdbc(dataSource)
    .domain("users")
    .interceptors(interceptors)
    .build();
```

- `assembly.sqlExecutor()` is injected into generated Mapper implementations.
- `assembly.transactionalExecutor()` creates the explicit local callback boundary.
- Calls outside a callback use temporary auto-commit handles.
- Calls inside a callback join one thread-bound root `SimpleTransaction`.
- Nested callbacks join the root; only the outer callback completes it.
- One assembly never coordinates commit with another assembly.

## SQL Provider

Use `@UseSqlProvider` on a Mapper method with a concrete `SqlProvider<P>` implementation.

- Use it for exceptional runtime SQL structure, not ordinary optional predicates.
- Return non-blank immutable `BoundSql` with a non-null ordered parameter list.
- The provider cannot be combined with XML or SQL annotations on the same method.
- Generated code holds one provider instance and invokes it directly without reflection.
- Provider SQL has weaker compile-time SQL validation because its final text is created at runtime.

## Parameter Binder

Use `@UseParameterBinder` on a Mapper parameter with a concrete `ParameterBinder<T>`.

- Use it when one parameter needs a representation outside Core standard routing.
- The binder type must match the annotated parameter type.
- The implementation must be visible, concrete, and have an accessible no-arg constructor.
- Generated execution carries a direct binder reference; there is no global reflective lookup.
- The explicit binder receives the value, including null, and fully replaces default parameter routing for that slot.
- Dynamic SQL carries generated binder slots aligned with emitted parameters. Providers carry either explicit binder metadata or Java/JDBC routing metadata through typed `BoundParameter` values; null values require the overload that supplies the Java type.

## Standard JDBC Routing

Standard route ownership and metadata are defined by [the Core contract](core-contract.md#25-standard-jdbc-type-routing). At execution time:

- parameters route from the generated Java type and canonical or explicitly declared `JDBCType`;
- result columns route from the generated Java target type and live `ResultSetMetaData`;
- each result route is resolved once per result set and reused for every row;
- generated source still invokes record constructors and JavaBean setters directly.

Applications do not register handlers, select package mappings, or add database-specific type artifacts. Core performs no database-product lookup, schema query, classpath scan, reflection-based object construction, or `ServiceLoader` discovery.

Unsupported scalar writes use `@UseParameterBinder`. Unsupported scalar reads or whole-row shapes use `@UseRowMapper`. Lifecycle-bound JDBC values such as LOBs, SQLXML, arrays, streams, and readers require a `RowMapper`, `ParameterBinder`, or raw JDBC because their resources cannot escape the executor cleanup boundary.

## Row Mapper

Use `@UseRowMapper` on a query method with a concrete `RowMapper<T>`.

- Use it for a one-row shape unsupported by scalar, record, or JavaBean generation.
- The mapper generic type must match the method's single result or `List<T>` element type.
- Explicit row mapping fully bypasses Core result routing for that method.
- A row mapper is a method-level, read-only escape hatch. It does not replace parameter binding.
- The row mapper runs only after `ResultSet.next()` succeeds.
- Generated code owns one mapper instance and passes a direct reference.

## Execution Interceptor

Register `ExecutionInterceptor` instances through `JdbcAssembly`, or expose them as ordered Spring beans with the starter.

- `beforeExecution` runs in configured order.
- `afterSuccess` and `afterFailure` run after executor-owned cleanup, in reverse order for interceptors whose before callback completed successfully.
- `ExecutionPlan` exposes immutable statement input and `ExecutionOutcome` exposes duration, affected rows, result count, and failure.
- The MVP contract is observational. It does not allow arbitrary SQL replacement or reflective mutation of generated binding and mapping.
- Terminal callback `RuntimeException` values are logged and isolated. They neither mutate the final failure tree nor prevent remaining terminal interceptors from observing the outcome. JVM `Error` values still propagate.
- Any interceptor callback adds runtime work; configure none when the direct path is preferred.

## Routing And Decorators

Prefer a physical or routing `DataSource` behind one explicit Mapper binding. The DataSource and its transaction manager own tenant context, shard selection, read/write routing, physical connection choice, and connection reuse.

Use a `SqlExecutor` decorator only for exceptional whole-execution behavior that cannot be represented by the DataSource or observational interceptor contracts. Such a decorator must preserve statement identity, parameter order, transaction-domain ownership, resource cleanup, and failure suppression. Lynxus does not provide an implicit routing decorator or put DataSource names in `ExecutionPlan`.

## Raw JDBC Boundary

Use raw JDBC when SQL shape, multi-row graph aggregation, vendor APIs, streaming, or resource control cannot fit the generated or typed extension contracts.

Keep this boundary explicit in repository structure and application code. Lynxus does not silently fall back to raw JDBC, runtime XML interpretation, Mapper proxies, or reflection.
