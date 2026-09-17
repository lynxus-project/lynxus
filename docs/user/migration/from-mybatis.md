---
title: Migrating From MyBatis
description: Migrate supported MyBatis Mapper SQL to Lynxus compile-time generation and replace SqlSession, OGNL, and plugin runtime features explicitly.
slug: docs/user/migration/from-mybatis
---

# Migrating From MyBatis

## 1. Classify Each Mapper Method

Use this decision order:

1. generated annotations or XML using the supported SQL subset;
2. typed `SqlProvider`, `ParameterBinder`, `RowMapper`, or `ExecutionInterceptor`;
3. raw JDBC for behavior that cannot fit the generated or typed contracts.

Do not choose a provider or raw JDBC only because the original Mapper used XML. Common XML should remain XML and compile into ordinary Java.

## 2. Migrate Mapper Declarations

- Replace MyBatis imports with Lynxus-owned types under `io.github.lynxus.annotation`.
- Keep `@Mapper`, `@Select`, `@Insert`, `@Update`, and `@Delete` method shapes.
- Use Lynxus `@Batch` for JDBC batch and `@GeneratedKey("id")` for one explicitly named generated-key column.
- Add explicit `@Param` names to multi-parameter methods.
- Prefer supported scalars, records, JavaBeans, and `List<T>` results.
- Inspect the generated `*MapperImpl` when diagnosing binding, dynamic SQL, or result mapping.

The annotation fixture is `lynxus-examples/basic-mapper/src/main/java/io/github/lynxus/example/UserMapper.java`.

## 3. Migrate XML

- Keep XML resources at the Mapper package resource path.
- Keep statement IDs equal to Mapper method names.
- Use the supported dynamic tags and expression subset.
- Replace data `${}` substitutions with bound `#{}` parameters.
- If XML and an annotation coexist, XML wins and javac warns on the Mapper method.

The XML fixture is `lynxus-examples/basic-mapper/src/main/resources/io/github/lynxus/example/UserXmlMapper.xml`.

Use XML `<batch>` or `@Batch` for `PreparedStatement.addBatch/executeBatch`. A normal `<insert>` containing `<foreach>` remains one dynamically generated SQL statement.

## 4. Replace Runtime OGNL Assumptions

Lynxus translates its controlled OGNL-like subset into Java during annotation processing. It does not execute OGNL, MVEL, SpEL, or another runtime expression engine.

```text
name != null and name != ''      -> name != null && !name.isEmpty()
ids != null and ids.size() > 0   -> ids != null && ids.size() > 0
values.length > 0                -> values.length > 0
'%' + name + '%'                 -> "%" + name + "%"
```

Arbitrary method calls, static calls, and unsafe SQL substitution fail compilation. Simplify the expression or use a typed provider.

## 5. Migrate Exceptional SQL And Mapping

- Use `@UseSqlProvider` only for runtime SQL structure such as validated identifiers or vendor-specific choices.
- Use `@UseParameterBinder` for one Java value type that cannot use JDBC `setObject`.
- Use `@UseRowMapper` for one unsupported row shape.
- Flatten simple `resultMap` declarations to records or JavaBeans.
- Use explicit follow-up queries or raw JDBC for multi-row nested graph aggregation.

Provider, binder, and row-mapper classes are compile-time validated, instantiated once per generated Mapper, and called directly without reflection.

## 6. Replace Plugins

Use `ExecutionInterceptor` for logging, metrics, tracing, audit, authorization, and slow-query observation.

- `beforeExecution` runs in registration order.
- `afterSuccess` and `afterFailure` unwind in reverse order.
- Interceptors observe immutable plan/outcome data.
- They cannot replace generated SQL, binders, or row mappers.

For dynamic tenant, shard, or read/write selection, prefer an application routing DataSource. Use a whole-`SqlExecutor` decorator only when DataSource routing cannot represent the requirement.

## 7. Assemble Standalone Runtime

Replace legacy engine, connection-provider, coordinator, or global configuration assembly with one `JdbcAssembly` per DataSource:

```java
JdbcAssembly assembly = Lynxus.jdbc(dataSource)
    .domain("users")
    .interceptors(interceptors)
    .build();

UserMapper mapper = new UserMapperImpl(assembly.sqlExecutor());
```

Wrap related Mapper calls in the callback executor:

```java
assembly.transactionalExecutor().execute(() -> {
    mapper.insert(...);
    mapper.update(...);
    return null;
});
```

Create independent assemblies for independent DataSources. Core does not provide distributed commit across them.

## 8. Assemble Spring Runtime

Keep Mapper package bindings explicit:

```yaml
lynxus:
  mapper-bindings:
    - package-name: com.example.user.mapper
      data-source: usersDataSource
    - package-name: com.example.order.mapper
      data-source: ordersDataSource
```

- Packages must be disjoint; duplicate, parent, and child bindings are rejected.
- One Mapper interface is registered once against one DataSource.
- Generated classes remain Spring-neutral and are registered by the starter.
- Each `@Transactional` boundary must use the transaction manager for the same DataSource.
- A physical or routing DataSource may be bound, but Lynxus does not own its routing context.

## 9. Verify Migration

Run the full compiler, standalone, Spring, and external fixtures:

```bash
mvn clean test
mvn -pl lynxus-core -am verify
```

Treat compilation diagnostics as migration tasks. Do not add runtime reflection or expression interpretation to bypass them.
