---
title: Choosing an Extension
description: Pick the narrowest Lynxus extension—provider, binder, row mapper, or interceptor—without replacing JDBC execution.
slug: docs/user/core/extensions
---

# Choosing an Extension

Start with generated annotation or XML SQL. Add the narrowest typed extension that owns the exceptional behavior instead of introducing a runtime registry or replacing the JDBC lifecycle.

| Need | Extension | Scope |
| --- | --- | --- |
| Supported Java/JDBC scalar value | Core `TypeHandlerManager` | Automatic, reads and writes |
| Exceptional binding for one parameter | `@UseParameterBinder` and `ParameterBinder<T>` | One Mapper parameter, writes only |
| Runtime SQL structure | `@UseSqlProvider` and `SqlProvider<P>` | One Mapper method |
| Unsupported result-row shape | `@UseRowMapper` and `RowMapper<T>` | One query method, reads only |
| Logs, metrics, audit, or authorization observation | `ExecutionInterceptor` | Executor assembly |
| Skip JDBC or replace an immutable plan | `ExecutionPlugin` | Executor assembly |
| Cache identical SELECT results | `CachingExecutionPlugin` and `QueryCache` | Executor assembly |
| Exceptional whole-execution routing | `SqlExecutor` decorator | Explicit application assembly |
| Behavior outside generated or typed contracts | Raw JDBC | Application-owned |

Parameter binders, providers, and row mappers are validated during compilation and directly referenced by generated code. Core standard routing uses generated Java types and JDBC metadata without scanning or reflective discovery. JDBC execution still belongs to `JdbcSqlExecutor`. Observational interceptors and `ExecutionPlugin` adapters wrap `SqlExecutor` outside that lifecycle.

Use [Choosing a Value or Row Mapping](mapping.md) for the detailed mapping decision. Exact visibility, constructor, lifecycle, thread-safety, null-handling, and host-integration rules are defined in the [Extension contracts](../../reference/extensions.md).
