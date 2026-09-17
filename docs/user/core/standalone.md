---
title: Standalone JDBC
description: Assemble generated Lynxus Mappers with JdbcAssembly and callback transactions, without Spring.
slug: docs/user/core/standalone
---

# Standalone JDBC

One `JdbcAssembly` represents one DataSource and transaction domain.

```java
DataSource dataSource = createDataSource();

JdbcAssembly assembly = Lynxus.jdbc(dataSource)
    .domain("users")
    .build();

UserMapper userMapper = new UserMapperImpl(assembly.sqlExecutor());
```

Calls outside an explicit transaction use temporary auto-commit connection handles. Use the assembly's callback executor when several Mapper calls must share one connection and commit or roll back together:

```java
User user = assembly.transactionalExecutor().execute(() -> {
    userMapper.insert(1L, "Alice");
    return userMapper.findById(1L);
});
```

`SqlExecutor` owns the fixed JDBC lifecycle. `ConnectionHandleFactory` supplies connection participation, while `TransactionalExecutor` owns the standalone callback boundary. Nested callbacks join the root transaction; a nested failure marks it rollback-only.

Create one assembly per DataSource. Lynxus does not coordinate distributed commits or dynamically bind one Mapper to multiple DataSources.

For lifecycle, failure, and transaction guarantees, see the [Core contract](../../reference/core-contract.md). For host-provided connection participation, see the [Extension contracts](../../reference/extensions.md).
