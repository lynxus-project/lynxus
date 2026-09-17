---
title: MyBatis Compatibility
description: Classify MyBatis 3.5 Mapper patterns as generated, converted, or explicitly rejected by the Lynxus compile-time SQL mapper.
slug: docs/reference/mybatis-compatibility
---

# MyBatis Compatibility

This document is the single compatibility classification for Lynxus. It compares Lynxus with
the deterministic Mapper behavior documented by MyBatis 3.5.19; it does not promise MyBatis API,
runtime, configuration, or plugin compatibility.

## Classification

Every row uses one of these statuses:

| Status | Meaning |
| --- | --- |
| Direct support | Lynxus has a documented contract and generated/runtime evidence for the behavior. |
| Deterministic conversion | A supported MyBatis shape can be rewritten into Lynxus declarations without runtime interpretation. |
| Skill-assisted migration | The behavior requires project inspection, ambiguity reporting, or a reviewable change produced by the migration skill in #10. |
| Explicit rejection | Lynxus rejects the behavior or deliberately does not provide its MyBatis runtime equivalent. |

The migration skill may assist with a row classified as `skill-assisted migration`, but it must not
invent a new compatibility rule. Compiler diagnostics and this document remain authoritative.

## Evidence And Baseline

MyBatis references:

- [MyBatis 3.5.19 source tree](https://github.com/mybatis/mybatis-3/tree/mybatis-3.5.19)
- [MyBatis getting started](https://mybatis.org/mybatis-3/getting-started.html)
- [MyBatis Java API](https://mybatis.org/mybatis-3/java-api.html)
- [MyBatis XML mapping](https://mybatis.org/mybatis-3/sqlmap-xml.html)
- [MyBatis dynamic SQL](https://mybatis.org/mybatis-3/dynamic-sql.html)
- [MyBatis Spring](https://mybatis.org/spring/)

Lynxus evidence:

- [Core contract](core-contract.md)
- [Extension contracts](extensions.md)
- [Manual migration guide](../user/migration/from-mybatis.md)
- [JDBC compatibility fixtures](../../lynxus-processor/src/test/java/io/github/lynxus/test/database)
- [XML compiler diagnostics](../../lynxus-processor/src/test/java/io/github/lynxus/test/UnsupportedMapperSignatureCompilationTest.java)
- [Generated-source tests](../../lynxus-processor/src/test/java/io/github/lynxus/test/generated)
- [External Maven consumer fixture](../../lynxus-examples/external-maven-processor)
- [External Gradle consumer fixture](../../lynxus-examples/external-gradle-processor)

The JDBC value-type comparison is owned by the Core contract and its PostgreSQL/MySQL tests. This
document classifies product behavior and links to that evidence; it does not duplicate the
handler-by-handler JDBC table.

## Compatibility Matrix

### Mapper API And SQL Sources

| MyBatis capability | Status | Lynxus boundary and migration action | Evidence |
| --- | --- | --- | --- |
| Mapper interfaces and CRUD methods | Deterministic conversion | Replace MyBatis annotation imports with `io.github.lynxus.annotation` declarations. Generated implementations are ordinary classes constructed with `SqlExecutor`. | [Core contract](core-contract.md), [migration guide](../user/migration/from-mybatis.md) |
| `@Select`, `@Insert`, `@Update`, `@Delete` | Direct support | Use the Lynxus equivalents. Static SQL and the controlled script subset compile into Java. | [Core contract](core-contract.md#21-sql-sources) |
| `@Param` and common parameter aliases | Direct support | Prefer explicit names for multi-parameter methods. Names and property paths are resolved at compile time. | [Core contract](core-contract.md#22-parameters) |
| `@Options` and arbitrary statement options | Explicit rejection | Lynxus currently emits default options from generated Mappers. Custom plan construction may set the documented JDBC options; no Mapper `@Options` contract exists. | [Core contract](core-contract.md#28-statement-options-and-pagination) |
| SQL providers | Deterministic conversion | Rewrite provider declarations to `@UseSqlProvider` for runtime SQL structure, with compile-time validation of provider shape and typed `BoundSql`. | [Extension contracts](extensions.md), [migration guide](../user/migration/from-mybatis.md#5-replace-exceptional-sql-and-mapping) |
| Custom parameter and result handlers | Deterministic conversion | Replace a parameter handler with `ParameterBinder` and a row/result handler with `RowMapper`; there is no global runtime handler registry. | [Extension contracts](extensions.md), [Core contract](core-contract.md#24-result-mapping) |

### XML And Dynamic SQL

| MyBatis capability | Status | Lynxus boundary and migration action | Evidence |
| --- | --- | --- | --- |
| Static Mapper XML statements | Direct support | Keep XML at the Mapper resource path. Supported declarations compile at annotation-processing time. | [Core contract](core-contract.md#21-sql-sources) |
| `select`, `insert`, `update`, `delete`, and `batch` declarations | Direct support | Use the controlled top-level subset. IDs must be non-blank and unique within the resource. | [Core contract](core-contract.md#21-sql-sources), [XML diagnostics](../../lynxus-processor/src/test/java/io/github/lynxus/test/UnsupportedMapperSignatureCompilationTest.java) |
| `if`, `choose`, `when`, `otherwise`, `trim`, `where`, `set`, `foreach`, `bind` | Deterministic conversion | Use the supported expression subset. Java control flow and SQL assembly are generated; no OGNL engine runs at runtime. | [Core contract](core-contract.md#21-sql-sources), [migration guide](../user/migration/from-mybatis.md#4-replace-runtime-ognl-assumptions) |
| `sql` and `include` fragments | Direct support | Fragment IDs and references are validated across the complete resource. Missing and cyclic references fail compilation. | [Core contract](core-contract.md#21-sql-sources), [XML diagnostics](../../lynxus-processor/src/test/java/io/github/lynxus/test/UnsupportedMapperSignatureCompilationTest.java) |
| XML namespaces and declaration integrity | Deterministic conversion | `<mapper namespace>` must equal the fully qualified Mapper name. Unsupported top-level tags, attributes, malformed declarations, and invalid unused declarations fail compilation. | [Core contract](core-contract.md#21-sql-sources), [XML diagnostics](../../lynxus-processor/src/test/java/io/github/lynxus/test/UnsupportedMapperSignatureCompilationTest.java) |
| `${}` substitution | Explicit rejection | Use bound `#{}` values or a provider for validated SQL structure. Lynxus never treats unsafe substitution as plain text. | [Core contract](core-contract.md#21-sql-sources), [migration guide](../user/migration/from-mybatis.md#4-replace-runtime-ognl-assumptions) |
| Arbitrary OGNL, static calls, and unsupported method calls | Explicit rejection | Rewrite into the supported expression subset or move SQL structure to a typed provider. | [Core contract](core-contract.md#29-compile-time-rejection) |
| Runtime XML reload or interpretation | Explicit rejection | Recompile after changing XML. XML is not loaded by the runtime executor. | [Design philosophy](../../Design-Philosophy.md#explicit-non-goals) |

### Result Mapping And Execution

| MyBatis capability | Status | Lynxus boundary and migration action | Evidence |
| --- | --- | --- | --- |
| Scalar, record, JavaBean, list, and optional results | Direct support | Use generated flat result mapping. Query cardinality is explicit through typed results and generated return adaptation. | [Core contract](core-contract.md#23-return-shapes), [Core contract](core-contract.md#24-result-mapping) |
| Flat `resultMap` declarations | Deterministic conversion | Use scalar mappings, JavaBean `<id>` / `<result>`, or record `<constructor>` arguments. All declarations and references are validated at compile time. | [Core contract](core-contract.md#24-result-mapping), [XML diagnostics](../../lynxus-processor/src/test/java/io/github/lynxus/test/UnsupportedMapperSignatureCompilationTest.java) |
| Nested `association`, `collection`, and graph aggregation | Explicit rejection | Flatten the query, use explicit follow-up queries, use a one-row `RowMapper`, or use raw JDBC. | [Core contract](core-contract.md#24-result-mapping), [migration guide](../user/migration/from-mybatis.md#5-replace-exceptional-sql-and-mapping) |
| Lazy loading and nested selects | Explicit rejection | Make loading explicit in application/service code. | [Design philosophy](../../Design-Philosophy.md#explicit-non-goals) |
| Generated keys | Direct support with constraints | Use one static insert with an explicit non-blank key column and one supported returned key value. Batch, dynamic, and provider generated keys are outside the contract. | [Core contract](core-contract.md#27-generated-keys) |
| JDBC batch execution | Direct support | Use Lynxus `@Batch` or XML `<batch>` with one `List<T>` argument and the driver-provided counts. | [Core contract](core-contract.md#23-return-shapes) |
| Cursor/streaming results | Deterministic conversion | Adapt cursor consumers to `RowCursor` callbacks. Cursors and streams cannot escape executor cleanup. | [Core contract](core-contract.md#23-return-shapes), [Extension contracts](extensions.md) |
| MyBatis built-in JDBC value handlers | Deterministic conversion | Use Lynxus's fixed Core routes. The supported Java/JDBC matrix and database evidence live in the Core contract. | [Core contract](core-contract.md#25-standard-jdbc-type-routing), [Core contract](core-contract.md#26-built-in-jdbc-types) |
| Unsupported or lifecycle-bound JDBC values | Explicit extension | Materialize through a `RowMapper`/`ParameterBinder` or use raw JDBC. JDBC resources do not escape cleanup. | [Core contract](core-contract.md#26-built-in-jdbc-types), [Extension contracts](extensions.md) |

### Sessions, Transactions, Spring, Plugins, And Caches

| MyBatis capability | Status | Lynxus boundary and migration action | Evidence |
| --- | --- | --- | --- |
| `SqlSession` and session-scoped runtime state | Explicit rejection | Inject or construct generated Mapper implementations with one `SqlExecutor`; transaction ownership is explicit. | [Design philosophy](../../Design-Philosophy.md#explicit-non-goals), [Core contract](core-contract.md#1-responsibility-boundary) |
| Local transactions | Direct support | Use one `JdbcAssembly` and its callback transaction executor. Nested failures preserve rollback-only semantics. | [Core contract](core-contract.md#5-standalone-transaction-contract) |
| Spring transaction participation | Deterministic conversion | Use the Spring starter for DataSource binding and transaction participation. Spring owns transaction policy; Core still owns JDBC execution. | [Extension contracts](extensions.md), [Spring guide](../user/spring/spring-boot.md) |
| Advanced propagation, savepoints, distributed transactions, and recovery | Explicit rejection | Delegate host transaction policy to Spring or another transaction system. Lynxus does not coordinate distributed commits. | [Core contract](core-contract.md#5-standalone-transaction-contract) |
| First/second-level cache | Explicit rejection | Use an application cache outside Lynxus. | [Design philosophy](../../Design-Philosophy.md#explicit-non-goals) |
| MyBatis plugin/interceptor chain | Explicit rejection | Use the narrow `ExecutionInterceptor` observation contract for logging, metrics, tracing, audit, and authorization. It cannot rewrite SQL or own JDBC execution. | [Extension contracts](extensions.md), [Core contract](core-contract.md#3-jdbc-execution-contract) |
| Runtime Mapper proxies | Explicit rejection | Use generated implementation classes directly or register them through the Spring starter. | [Core contract](core-contract.md#1-responsibility-boundary) |

## Migration Decision Rules

1. Keep a method in annotations or XML when its SQL, parameters, dynamic branches, and flat result shape fit a `direct support` or `deterministic conversion` row.
2. Use a typed provider, binder, row mapper, interceptor, or Spring adapter only when the corresponding Lynxus contract owns the behavior.
3. Send ambiguous or project-wide transformations to the migration skill. It must produce a reviewable diff and intervention report rather than guess.
4. Stop and report the construct for `explicit rejection`; do not add runtime reflection, OGNL, a global registry, a session abstraction, or a SQL-rewrite plugin to bypass the boundary.

The manual workflow is documented in [Migrating From MyBatis](../user/migration/from-mybatis.md). The
automated, reviewable workflow is owned by issue #10 and must consume this matrix rather than copy it.
