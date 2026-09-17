---
title: Choosing a Value or Row Mapping
description: Choose standard JDBC routing, generated result mapping, a parameter binder, or a row mapper in Lynxus.
slug: docs/user/core/mapping
---

# Choosing a Value or Row Mapping

Lynxus has one generated default path and two custom mapping escape hatches:

- Core standard routing converts supported single JDBC values in both directions.
- Generated result assemblers construct scalars, records, and JavaBeans inside the executor lifecycle.
- `ParameterBinder` handles one exceptional Java-to-JDBC parameter.
- `RowMapper` handles one exceptional JDBC-row-to-Java result.

There is no package mapping selection, user TypeHandler registry, result-side JDBC type annotation, or database-specific type module.

## Generated Result Mapping

Use ordinary Mapper parameters and scalar, record, or JavaBean results whenever Core supports the values:

```java
@Select("SELECT id, name FROM users WHERE id = #{id}")
User findById(long id);
```

Generated code directly invokes record constructors and JavaBean setters. It does not construct objects through reflection.

When result labels differ from Java names, use SQL aliases, method-level `@Results` / `@Result`, or XML `resultMap`:

```java
@Select("SELECT user_id, display_name FROM users WHERE user_id = #{id}")
@Results({
    @Result(column = "user_id", property = "id"),
    @Result(column = "display_name", property = "name")
})
User findById(long id);
```

Annotation and XML forms normalize into the same compiler model. They cannot both configure one method, and neither can be combined with `@UseRowMapper`. Current result mappings are flat: one column maps to one scalar, record component, or JavaBean property. Associations and collections are future work.

Generated Mapper implementations carry ordinary mappings in a typed `QueryExecutionPlan<T>`. Core
converts each JDBC value first and passes a read-only `ResultRow` to the generated
`ResultAssembler<T>`. Generated query methods consume `QueryResult<T>` and expose non-null rows,
`oneOrNull()`, `optional()`, or `required()` according to the declared return shape. Update,
generated-key, and JDBC batch methods consume `UpdateResult`, `GeneratedKeyResult<K>`, and
`BatchResult` respectively. Application code does not implement or configure result assemblers.

## Standard JDBC Routing

Standard routing requires no application setup; its ownership and metadata contract are defined in [Standard JDBC Type Routing](../../reference/core-contract.md#25-standard-jdbc-type-routing). For results, the runtime reads each column's JDBC type once, combines it with the generated Java target type, and resolves a typed handler once per result column. Each row then calls that fixed handler without reading metadata or repeating route selection.

Users do not configure or register this manager. It has no database-product branches, schema lookup, classpath scanning, or `ServiceLoader`.

Ordinary character, numeric, temporal, enum, UUID, and binary columns need no result annotation. If the driver reports an unsupported JDBC type for the target Java type, mapping fails with the result column, Java type, JDBC type, and guidance to use `@UseRowMapper`.

Lifecycle-bound values such as `BLOB`, `CLOB`, `NCLOB`, `SQLXML`, JDBC `ARRAY`, streams, and readers are not ordinary scalar results. Use `RowMapper` while the `ResultSet` is active, or raw JDBC when the application must own resource lifetime. Lynxus does not provide `@ResultJdbcType`.

## Choose by Scope

| Need | Direction | Scope | Use |
| --- | --- | --- | --- |
| Supported scalar or object mapping | Both | Generated Mapper method | Core standard routing plus generated mapping |
| Exceptional parameter representation | Java → JDBC | One parameter | `@UseParameterBinder` |
| Exceptional result conversion or row shape | JDBC → Java | One query method | `@UseRowMapper` |
| Application-owned resource or multi-row graph | Application-defined | Explicit boundary | Raw JDBC |

## Parameter Binder

`ParameterBinder<T>` fully owns one annotated parameter, including null handling:

```java
int insert(
    @UseParameterBinder(BinaryUuidBinder.class)
    UUID id
);
```

Use it when a value needs a non-standard representation such as binary UUID storage or a vendor object. The compiler validates the binder type, visibility, constructor, and generic target, and generated code invokes it directly.

A binder is write-only. It does not affect query result mapping.

## Row Mapper

`RowMapper<T>` fully owns result reading for one query method:

```java
@UseRowMapper(UserSummaryRowMapper.class)
@Select("SELECT u.id, u.name, count(o.id) AS order_count FROM users u ...")
UserSummary findSummary(long id);
```

Use it when one row needs custom construction, several columns participate in one conversion, or a JDBC value must be consumed while resources are active. Generated code holds one mapper instance and passes it directly to `JdbcSqlExecutor`.

A row mapper is read-only and method-specific. Parameters on the same method still use Core routing or their own `ParameterBinder`.

## Compilation and Runtime Boundary

```text
compile time:
Mapper annotation/XML
  -> normalized SQL and result mapping
  -> generated parameter types, result target types, constructors, and setters

runtime:
generated execution plan
  -> JdbcSqlExecutor reads each result column JDBC type once
  -> TypeHandlerManager resolves a typed handler for JDBC type + Java target type
  -> the fixed handler reads and converts that column for every row
  -> JdbcSqlExecutor executes one JDBC lifecycle
  -> generated code casts/unboxes values and constructs the final Java result
```

Use the generated path first. Add a binder only for an exceptional write, a row mapper only for an exceptional read, and raw JDBC when the application must own behavior outside these contracts.

The exact supported types, validation, lifecycle, and concurrency guarantees are defined by the [Core Contract](../../reference/core-contract.md) and [Extension Contracts](../../reference/extensions.md).
