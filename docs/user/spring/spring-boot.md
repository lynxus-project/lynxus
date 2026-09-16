---
title: Spring Boot AOT ORM Integration
description: Integrate Lynxus with Spring Boot, named DataSources, explicit transactions, and Spring AOT.
slug: docs/user/spring/spring-boot
---

# Spring Boot Integration

The Spring Boot starter registers generated Mapper implementations and binds each Mapper package to one named DataSource. Generated classes remain Spring-neutral and continue to depend only on `SqlExecutor`.

## Configure Mapper Packages

Registration is explicit even when the application has one DataSource:

```yaml
lynxus:
  enabled: true
  mapper-bindings:
    - package-name: com.example.user.mapper
      data-source: usersDataSource
    - package-name: com.example.order.mapper
      data-source: ordersDataSource
```

Each binding owns the generated Mappers in that package and its subpackages:
`com.example.user.mapper` uses `usersDataSource`, while
`com.example.order.mapper` uses `ordersDataSource`. Packages must be disjoint;
overlapping parent and child rules fail at startup instead of selecting a
DataSource implicitly.

The annotation processor emits one independent metadata resource per generated
Mapper under
`META-INF/lynxus/mappers/<generated-implementation-class>.properties`.
At startup the starter loads these resources from the runtime classpath and:

1. matches each metadata package to one configured binding;
2. resolves the named Spring `DataSource` bean;
3. creates one Spring-aware `SqlExecutor` for that DataSource;
4. registers each implementation under the JavaBeans-decapped interface name.

Generated Mapper implementations remain plain Java classes. The processor does
not parse Spring configuration files, generate `@Component`, or require a
Spring-specific compiler option. A class named `*MapperImpl` without processor
metadata is not discovered.

Mapper package bindings must not overlap. Applications with several DataSources use disjoint Mapper packages and a matching transaction manager for each domain.

Package binding selects only the Spring `DataSource` domain. It does not select a
JDBC mapping family. Standard Java/JDBC values are routed by Core's fixed,
database-independent `TypeHandlerManager`; the Starter does not inspect the
database product, register package mappings, or choose handlers per package.

## Transactions

Inside `@Transactional`, `SpringConnectionHandleFactory` obtains and releases the thread-bound connection through `DataSourceUtils`. Spring owns commit and rollback timing; Lynxus does not duplicate the transaction boundary.

The `PlatformTransactionManager` must manage the same DataSource named by the Mapper package binding. A mismatch fails explicitly.

## Read/Write Routing

For read/write separation, bind the Mapper package to one Spring
`AbstractRoutingDataSource`. Spring owns the route context and maps keys to the
read and write pools; Lynxus sees only the routing DataSource:

```java
@Bean
DataSource readWriteDataSource(
        @Qualifier("writerDataSource") DataSource writer,
        @Qualifier("readerDataSource") DataSource reader) {
    AbstractRoutingDataSource routing = new AbstractRoutingDataSource() {
        @Override
        protected Object determineCurrentLookupKey() {
            return ReadWriteContext.current();
        }
    };
    routing.setDefaultTargetDataSource(writer);
    routing.setTargetDataSources(Map.of("read", reader, "write", writer));
    return routing;
}
```

Bind the package to `readWriteDataSource`:

```yaml
lynxus:
  mapper-bindings:
    - package-name: com.example.mapper
      data-source: readWriteDataSource
```

Application code sets `ReadWriteContext` before invoking a Mapper and clears it
in a `finally` block. The route is selected when Spring obtains the connection;
do not change the key during one transaction. Configure a transaction manager
for the same routing DataSource so Spring and Lynxus share one transaction
domain. This policy is exercised by the Starter's routing integration tests.

## Version and Consumer Verification

Java 21 is the current baseline. The repository build uses Spring Boot 3.1.5;
the external consumer and runtime Starter suite are verified against Boot 3.1.5,
3.5.16, and 4.1.1. These anchors do not automatically cover every minor or
patch release; an unlisted line remains unverified until it passes both gates. The
independent consumer fixture is documented in
[`external-spring-boot-consumer`](../../../lynxus-examples/external-spring-boot-consumer/README.md).
For a complete two-package setup, see the runnable
[`multi-datasource-spring-boot`](../../../lynxus-examples/multi-datasource-spring-boot/README.md)
example.

## Extension Beans

Spring may discover and order `ExecutionInterceptor` beans. SQL providers, parameter binders, type handlers, and row mappers remain compile-time-selected generated dependencies rather than global runtime registries.

See the [Extension contracts](../../reference/extensions.md) for exact validation, lifecycle, and ownership rules.
