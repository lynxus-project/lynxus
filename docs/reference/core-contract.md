---
title: Lynxus Core GA Contract
description: Normative Lynxus Core contract for Mapper SQL methods, JDBC execution, failures, transactions, and mapping boundaries.
slug: docs/reference/core-contract
---

# Lynxus Core GA Contract

Date: 2026-09-08

This document defines the supported contract of `lynxus-core` for the first GA release. It is intentionally narrower than MyBatis and narrower than the Spring Boot integration. Anything not listed here is unsupported unless another public contract explicitly says otherwise.

## 1. Responsibility Boundary

Core owns:

- compile-time Mapper validation, SQL normalization, dynamic SQL generation, parameter ordering, and typed result mapping;
- generated `*MapperImpl` classes whose only constructor dependency is `SqlExecutor`;
- immutable execution plans and the fixed JDBC execution lifecycle;
- typed provider, binder, row-mapper, cursor, interceptor, execution-plugin, connection-participation, and transaction-callback contracts;
- a minimal standalone JDBC assembly and local transaction implementation.

Core does not own:

- dependency injection or Mapper bean discovery;
- connection pooling, routing DataSource implementation, tenant or shard context;
- declarative transaction propagation, savepoints, distributed transactions, or transaction recovery;
- schema migration, session-scoped cache lifecycle or invalidation, lazy loading, nested object
  aggregation, or a MyBatis plugin runtime;
- framework pagination models or automatic count queries. Explicit `PaginationDialect` adapters may
  replace a SELECT plan at the `SqlExecutor` boundary.

Spring integration is a host adapter. It registers generated classes and supplies Spring-aware connection participation, while the generated Mapper and `JdbcSqlExecutor` remain Spring-neutral.

## 2. Mapper Compilation Contract

### 2.1 SQL Sources

Supported SQL sources are:

- `@Select`, `@Insert`, `@Update`, and `@Delete`;
- `@Batch` for static JDBC batch statements;
- Mapper XML statements;
- `@UseSqlProvider` for typed runtime SQL construction.

When XML and a SQL annotation define the same Mapper method, XML wins and javac reports a warning on that method. Lynxus does not load or reinterpret Mapper XML at runtime.

Effective Mapper SQL methods cannot be overloaded. Lynxus reports all conflicting resolved signatures before parsing their SQL source so statement identity remains unambiguous.

Supported dynamic SQL elements are `if`, `choose`, `when`, `otherwise`, `trim`, `where`, `set`, `foreach`, `bind`, `sql`, and `include`. Supported expressions are compiled to Java. Arbitrary OGNL, static method access, and unsupported method calls fail compilation. Unsafe `${...}` substitution is rejected; values use `#{...}` and exceptional SQL structure uses a typed provider.

Mapper XML must have a `<mapper>` root with a non-blank `namespace` equal to the Mapper's fully qualified name. Its direct declarations are limited to `select`, `insert`, `update`, `delete`, `batch`, `sql`, and `resultMap`. Statement IDs and SQL fragment IDs must be non-blank and unique within the resource. Statement `resultMap` and `<include refid>` references are resolved at compile time across the complete resource; missing and cyclic references fail compilation instead of being ignored or overwritten. XML statement attributes are limited to the documented `id`, `resultType`, and `resultMap` attributes, and no Mapper `@Options` contract currently exists.

Generated dynamic methods use one invocation-scoped `BoundSqlBuilder`. The builder owns SQL-fragment spacing, `where`, `set`, and `trim` normalization, and atomic placeholder registration with its value, optional binder, Java type, and JDBC type. Child fragments retain their parameters only when the enclosing clause is emitted. `build()` produces immutable `BoundSql` with statement-aware failures for blank SQL or incompatible fragment composition. This generated-code seam is not an application query DSL and does not interpret XML or expressions at runtime.

Mapper XML and annotation `<script>` content use one fail-closed parser configuration. `DOCTYPE`, external entities, external DTD loading, XInclude, and external schema access are disabled; failure to enforce the required parser controls rejects compilation. Lynxus does not resolve referenced external XML resources from the filesystem or network during SQL parsing.

### 2.2 Parameters

Regular annotation/XML methods may have zero or more parameters. Parameter references support explicit `@Param` names and the documented fallback names such as declared names, `param1`, `arg0`, `list`, `collection`, and `array`. Property paths are resolved at compilation and generated as direct Java access.

Additional parameter rules:

- varargs and static Mapper methods are rejected;
- unresolved generic parameter types are rejected;
- a provider method accepts zero or one Mapper argument; multiple inputs should be wrapped in a record or another value object;
- a batch method requires exactly one `List<T>` argument and binds each item under `item`;
- null values bind JDBC `NULL`; an explicit `ParameterBinder` owns null handling for its parameter;
- provider parameters preserve explicit order through `BoundSql` and `BoundParameter`; routed values use `BoundParameter.of(javaType, value[, jdbcType])` so runtime routing retains the declared type even for nulls, interfaces, and supertypes.

### 2.3 Return Shapes

SELECT methods support:

- a nullable reference result for zero or one row;
- `Optional<T>` for zero or one row;
- `List<T>` for zero or more rows;
- scalar, record, JavaBean, or explicit `@UseRowMapper` element mapping;
- callback-scoped cursor consumption with exactly one `CursorCallback<T, R>` whose result type matches the Mapper method return type.

A single-result method throws `NonUniqueResultException` when more than one row is returned. Primitive SELECT return types are rejected because they cannot represent zero rows. `RowCursor` and `Stream` cannot escape the callback scope.

INSERT, UPDATE, and DELETE methods return `void`, `int`, or `long`, where numeric values are JDBC update counts. Batch methods return the driver-provided `int[]` update counts.

### 2.4 Result Mapping

Built-in mapping is generated for:

- scalar values from one column;
- records through their canonical constructor;
- JavaBeans through a usable no-argument constructor and supported setters;
- lists and optionals of supported element types.

For a non-cursor SELECT method, generated code creates a typed `QueryExecutionPlan<T>` carrying a
`ResultAssembler<T>`. `JdbcSqlExecutor` first reads and converts column values through standard Core
type routing, then invokes the assembler before closing the result set. The resulting
`QueryResult<T>` contains a non-null immutable `List<T>` and provides `oneOrNull()`, `optional()`,
and `required()` for single-row contracts, so generated Mapper return handling does not expose or
remap `Object[]` rows. Updates use `UpdateResult`, generated-key inserts use
`GeneratedKeyResult<K>`, and JDBC batches use `BatchResult`. `SqlResult` remains the compatibility
union returned by the low-level `execute(ExecutionPlan)` entry point; typed `SqlExecutor` methods
adapt it without creating a second JDBC lifecycle. Direct low-level `ExecutionPlan` queries
continue to return immutable raw row arrays.

Generated Mappers separate stable statement definitions from invocation bindings. Fixed SQL methods
retain their statement identity, SQL text, source, options, extension references, type routing, and
result assembly in immutable `QueryDefinition`, `CommandDefinition`, or `BatchDefinition` fields.
Their execution-plan factories bind only ordered invocation values. Dynamic and provider queries bind
validated `BoundSql` to a query definition so their variable SQL and parameter routes remain
invocation-scoped while result routing and assembly remain fixed. Definitions never retain Mapper
argument values.

Column labels are matched case-insensitively. SQL aliases, method-level `@Results` / `@Result`, or XML `resultMap` metadata may define explicit labels. Result mappings are flat: each entry maps one column to one record component or JavaBean property, while a scalar mapping declares one column without a property. XML records use `<constructor>` with `<arg>` / `<idArg>` entries; JavaBeans use `<id>` / `<result>`. XML and annotation mappings normalize into the same compiler model and cannot both configure one method or be combined with `@UseRowMapper`. Missing required columns, duplicate labels, unsupported conversions, invalid row widths, and construction failures are mapping errors rather than silent fallback.

Custom `RowMapper<T>` and `ParameterBinder<T>` implementations are selected at compilation, instantiated once per generated Mapper instance, and invoked directly without reflection dispatch. They must be stateless, thread-safe, or externally synchronized.

### 2.5 Standard JDBC Type Routing

`JdbcSqlExecutor` owns one fixed `TypeHandlerManager` with the Core route set. Generated Mappers carry only aligned Java/JDBC routing metadata and explicit extension references; they do not import, create, configure, or invoke the manager. No package annotation, handler registration, database-specific artifact, runtime registry, or discovery step is required.

Generated code supplies declared Java parameter types and optional placeholder `jdbcType` values. Without an explicit value, the compiler emits the canonical JDBC type, including a stable type for null parameters. Unsupported parameter types fail compilation with guidance to use `@UseParameterBinder`.

For query results, generated code supplies each Java target type, result label, and result assembler. `JdbcSqlExecutor` reads each result column's JDBC type once, combines it with the generated target type, resolves one typed result handler per column, and reuses that handler for every row. The handler owns the JDBC getter and conversion to its target Java type. The generated assembler then casts or unboxes those converted values for direct scalar return, record construction, or JavaBean setter calls. Unsupported runtime result pairs fail in the mapping phase with the result column, Java target type, JDBC type, and guidance to use `@UseRowMapper`.

The manager has no database-product branches, vendor-type registry, schema access, classpath scanning, reflection-based object construction, or `ServiceLoader`. Exceptional scalar representations are deliberately query- or parameter-scoped through `RowMapper` and `ParameterBinder`.

### 2.6 Built-In JDBC Types

Generated scalar, record, and JavaBean mappings support numeric primitives and wrappers, `String`, `Character`, `Boolean`, enum names and ordinals, `BigDecimal`, `BigInteger`, `LocalDate`, `LocalDateTime`, `Instant`, `UUID`, `LocalTime`, `OffsetDateTime`, `byte[]`, boxed `Byte[]`, `java.util.Date`, the three `java.sql` date/time types, `Year`, `Month`, `YearMonth`, and `JapaneseDate`.

Canonical parameter routes are:

| Java type | Canonical JDBC representation |
| --- | --- |
| Numeric primitives/wrappers, `BigDecimal`, `BigInteger` | Matching numeric JDBC type |
| `Boolean` / `boolean` | `BOOLEAN` |
| `Character` / `char`, `String`, `YearMonth` | Character JDBC type |
| `byte[]`, `Byte[]` | `VARBINARY` |
| `LocalDate`, `java.sql.Date`, `JapaneseDate` | `DATE` |
| `LocalTime`, `java.sql.Time` | `TIME` |
| `LocalDateTime`, `Instant`, `java.util.Date`, `java.sql.Timestamp` | `TIMESTAMP` |
| `OffsetDateTime` | `TIMESTAMP_WITH_TIMEZONE` |
| `Year`, `Month` | `INTEGER` |
| `UUID` | `OTHER` |
| Enum | `VARCHAR` name |

A placeholder may select another compatible JDBC representation, such as an enum ordinal with `jdbcType=INTEGER`, a character UUID, national-character string types, or the legacy `java.util.Date` date-only and time-only routes with `jdbcType=DATE` or `jdbcType=TIME`. Result routes use the JDBC type reported by the active driver. Enum character values use names and numeric values use zero-based ordinals. Null reference values remain null.

Lifecycle-bound values such as `BLOB`, `CLOB`, `NCLOB`, `SQLXML`, JDBC `ARRAY`, streams, and readers are not standard scalar routes because executor cleanup owns their JDBC resources. Use `ParameterBinder`, `RowMapper`, or raw JDBC.

The executable contract is verified by:

```bash
mvn -pl lynxus-core -am \
  -Dtest=JdbcTypeCompilationTest,TypeHandlerManagerRoutingTest,ResultValueConvertersTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
mvn -pl lynxus-core -am \
  -Dtest=PostgresCompatibilityTest,MySqlCompatibilityTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

### 2.7 Generated Keys

Generated keys require all of the following:

- an INSERT method;
- static annotation or XML SQL;
- an explicit non-blank key column, for example `@GeneratedKey("id")`;
- exactly one returned key row and one returned key column;
- a supported scalar return type or an explicit `@UseRowMapper`.

Generated keys are not supported for batch methods, dynamic SQL, or SQL providers. Lynxus prepares the statement with the declared key-column name so drivers such as PostgreSQL do not return an entire inserted row by default.

### 2.8 Statement Options And Pagination

`StatementOptions` supports JDBC query timeout, fetch size, and max rows on an `ExecutionPlan` or `BatchExecutionPlan`:

- timeout and fetch size are positive integers when present;
- max rows is non-negative when present;
- absent values do not call the corresponding JDBC setter.

Generated Mapper methods currently emit default statement options; there is no Mapper `@Options` contract. Custom plan construction may set options explicitly.

`maxRows` is a JDBC safety ceiling, not pagination. Real pagination must place dynamic limit/offset or equivalent dialect SQL in the final SQL text so the database performs bounded work.

### 2.9 Compile-Time Rejection

Unsupported Mapper behavior fails compilation instead of falling back to runtime interpretation or plain-text SQL. Diagnostics are attached to the Mapper method when javac can represent the location and include stable Mapper, resolved-method, source, and resource context known without guessing. One Mapper stops after its first deterministic rejection, while independent Mapper interfaces continue processing in the same javac invocation.

## 3. JDBC Execution Contract

`JdbcSqlExecutor` owns this fixed JDBC order:

```text
validate -> open handle -> acquire connection
-> prepare -> apply statement options -> bind -> execute -> read/map
-> deactivate cursor -> close ResultSet -> close statement -> close handle
-> form final outcome
```

Observational `ExecutionInterceptor` instances wrap `SqlExecutor` outside that JDBC lifecycle. Registration order is outer to inner: `before` callbacks run, then `next` (ultimately JDBC), then reverse `afterSuccess` / `afterFailure`. The adapter synthesizes `ExecutionOutcome` from the `next` call; duration is the `next` wall time and includes JDBC cleanup. Terminal adapter callback failures stay isolated.

The JDBC sequence is not a pluggable phase chain. SQL structure, value binding, row mapping, observation, and host connection participation use their dedicated typed contracts.

Standalone and hosted integrations share this exact executor lifecycle. A host may supply connection participation and own transaction completion, but it does not prepare statements, bind values, execute SQL, read or map results, close executor-owned resources, or publish execution outcomes.

Resource ownership rules:

- each non-cursor execution closes its `ResultSet`, statement, and connection handle in reverse ownership order;
- a cursor is valid only while its callback is executing and is deactivated before cleanup and terminal observation;
- cleanup failures do not replace an earlier SQL or cursor callback failure; they are suppressed once on the primary cause in result-set, statement, and connection-handle order;
- a cleanup failure after successful JDBC execution is reported as `SqlExecutionException` with phase `CLEANUP`;
- the final `ExecutionOutcome.failure()` is the same throwable delivered to the Mapper caller for ordinary runtime failures;
- outcome duration includes execution cleanup but excludes terminal interceptor time;
- terminal interceptor runtime failures are logged individually, do not mutate the final outcome, and do not prevent remaining terminal interceptors from running.

An execution outcome covers the executor-owned lifecycle only. It includes `ConnectionHandle.close()` but does not claim that a standalone or Spring transaction committed or rolled back.

## 4. Failure Contract

All framework runtime failures derive from `LynxusException`. The main categories are:

- `ConfigurationException` for assembly and extension-contract failures;
- `SqlExecutionException` for physical JDBC lifecycle failures;
- `MappingException` and `NonUniqueResultException` for result-contract failures;
- `TransactionException` for local transaction begin, commit, rollback, rollback-only, cleanup, and domain failures.

`SqlExecutionException` reports statement ID, SQL source, execution phase, and JDBC execution certainty:

| State | Meaning |
| --- | --- |
| `NOT_EXECUTED` | JDBC execution was not attempted, including an empty batch that performs no `executeBatch()` call. |
| `OUTCOME_UNKNOWN` | The JDBC execute call was entered but threw before Lynxus received a result; retry safety depends on the operation and database. |
| `EXECUTED` | JDBC returned and later result reading, mapping, observation, or cleanup failed. |

These states do not claim commit or rollback. Transaction completion belongs to the local `TransactionalExecutor` or the host transaction manager.

Default exception messages do not include final SQL text, parameters, row arrays, or configuration values. Explicit `SqlExecutionException.Diagnostics` exposes SQL for deliberate diagnostic handling. Original JDBC causes remain available, including `BatchUpdateException` partial counts and driver SQLState values.

## 5. Standalone Transaction Contract

One `JdbcAssembly` represents one DataSource and transaction domain. Calls outside `transactionalExecutor().execute(...)` use independent auto-commit handles.

For a root callback:

- one local transaction is bound to the current thread;
- the connection is acquired lazily on the first Mapper call;
- all Mapper calls through the same assembly join that connection;
- success commits once, failure rolls back when required, and cleanup always clears the thread binding.

Nested callbacks join the root. A nested failure marks the root rollback-only even when application code catches the original failure. The root then rolls back and reports `TransactionException.Type.ROLLBACK_ONLY` rather than committing partial work.

The standalone implementation intentionally does not provide propagation enums, savepoints, isolation/read-only declarations, suspend/resume, distributed commit, or recovery. Production Spring applications use Spring transaction management for those host concerns.

## 6. DataSource Domain Contract

Mapper/DataSource ownership is one-to-one:

- one generated Mapper instance receives one `SqlExecutor`;
- one executor graph belongs to one physical or routing DataSource;
- multiple DataSources use independent assemblies or disjoint Spring Mapper-package bindings;
- `ExecutionPlan` contains no DataSource name and performs no runtime bean lookup;
- core does not coordinate atomic commits across assemblies.

A routing DataSource may remain the Mapper's single bound DataSource. Tenant, shard, read/write, and physical routing policies then belong to that DataSource and its transaction manager.

## 7. Concurrency Contract

Generated Mappers and `JdbcSqlExecutor` keep execution-local JDBC state in method scope. Plan inputs are defensively copied, and result rows and batch counts are returned through defensive copies.

The same generated Mapper instance may be called concurrently when its provider, binder, row-mapper, and interceptor instances are themselves thread-safe. Standalone transaction bindings are instance-scoped `ThreadLocal` values: a root connection is not shared across threads, nested calls on one thread join correctly, and completion removes the binding before later non-transactional calls.

## 8. Database Compatibility Gate

The GA compatibility gate runs one shared contract against:

| Database | Pinned test image | Covered behavior |
| --- | --- | --- |
| PostgreSQL | `postgres:16.4-alpine` | standard scalar routing, record/JavaBean mapping, dynamic SQL, local transactions, rollback-only, generated keys, batch, timeout, temporal values, identifier strings, binary values, cursor scope |
| MySQL | `mysql:8.4.0` | the same shared contract, with vendor-specific fixture DDL and timeout SQL only |

Database-backed Core compatibility, multi-DataSource, concurrency, and transaction contracts execute on both pinned engines. H2 is reserved for the separate JMH benchmark baseline and is not a functional compatibility fixture.

## 9. Explicit Non-Goals For Core GA

Core GA does not promise:

- arbitrary OGNL or complete MyBatis XML compatibility;
- complex `resultMap` graphs, nested collections, lazy loading, or session-scoped/second-level cache;
- runtime Mapper proxies, runtime XML reload, or reflection-based dispatch;
- same-Mapper multi-DataSource binding;
- automatic count queries, framework pagination models, or a pagination DSL;
- automatic retries after `OUTCOME_UNKNOWN`;
- connection pooling, distributed transactions, or production transaction policy;
- undocumented compiler implementation classes or JDBC phase interceptors as public extension APIs.

Future capabilities must preserve these ownership boundaries or update this contract with executable tests.
