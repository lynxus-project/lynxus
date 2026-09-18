# Lynxus Design Philosophy

> "First principles are the strongest weapon for reasoning." — Elon Musk
>
> "I don't want to improve the carriage; I want to invent the automobile." — Henry Ford

## Purpose

Lynxus exists to provide a smaller and more predictable compile-time Java ORM for teams that value explicit SQL, compile-time feedback, readable generated code, and direct JDBC behavior. It is not a mapper tool and not a Hibernate or JPA Session ORM.

MyBatis 3.5.x is Lynxus's compatibility baseline for deterministic mapping behavior and JDBC value types. Lynxus does not copy MyBatis runtime architecture, but a migration should not lose a deterministic mapping merely because Lynxus omitted the corresponding built-in type. Success means teams can adopt Lynxus through normal Maven or Gradle dependencies, migrate supported Mapper code with limited friction, understand generated behavior, and diagnose failures without framework internals. Hibernate is not the comparison target.

## Core Model

The durable Lynxus model is:

```text
Mapper interface + annotations/XML
        |
        v
Annotation processor
        |
        v
Validated compilation model
        |
        v
Generated Mapper implementation
        |
        v
SqlExecutor
        |
        v
Fixed JDBC lifecycle
```

Compile-time work includes:

- Mapper method validation;
- SQL source selection;
- dynamic SQL expression compilation;
- parameter and type-handler planning;
- return-shape and result-mapping validation;
- readable Java source generation.

Runtime work includes:

- acquiring a connection handle;
- preparing and configuring a JDBC statement;
- binding parameters;
- executing SQL;
- reading or mapping results;
- closing resources;
- publishing one final execution outcome.

## Architectural Principles

### Compile Stable Knowledge

Anything that can be determined reliably by javac should not be rediscovered on every Mapper call. Unsupported signatures, unknown parameters, invalid XML, unsafe substitution, incompatible result types, and unsupported dynamic expressions should fail during compilation.

Compile-time processing must remain deterministic. The processor must not depend on network resources or
runtime container state. A processor-local template engine may render the final source layout from a
structured model, but it must not carry SQL semantics, type decisions, diagnostics, or dynamic-expression
evaluation.

### Keep Runtime Explicit

Generated Mapper implementations are ordinary Java classes. They receive a `SqlExecutor`, retain immutable compile-time statement definitions, bind invocation values into execution plans, and adapt typed execution results to the declared return shape. Standard query definitions carry generated result assemblers so object construction remains compile-time-defined while the executor owns result mapping and JDBC cleanup.

Lynxus does not expose a session abstraction and does not use runtime Mapper proxies. The runtime path should remain visible in generated source and debuggable with normal Java tools.

### Keep One JDBC Lifecycle

`JdbcSqlExecutor` owns the physical statement lifecycle. Standalone and hosted integrations may replace connection participation and transaction ownership, but they must not fork or reimplement statement preparation, binding, execution, result reading, mapping, cleanup, or interceptor completion.

Every acquired resource has one owner. Cleanup failures remain observable, original failures remain primary, and terminal observation failures do not overwrite SQL or cleanup failures.

### Prefer Proven Solutions and Exercise Engineering Judgment

Engineering principles are guides for judgment, not rigid procedures. Before creating a custom design, first determine whether a mature industry standard, library, protocol, pattern, or implementation already solves the problem.

When a proven solution matches Lynxus's requirements, architectural boundaries, dependency constraints, and ownership model, adopt it directly. Do not rebuild an established solution merely to follow an internal process or demonstrate architectural purity.

Maturity alone is not sufficient. A solution must also fit the actual context. Consider its correctness, maintenance status, complexity, dependencies, operational cost, failure behavior, security, compatibility, and long-term ownership.

When no mature solution fits, identify the essential responsibilities, data, invariants, boundaries, and failure modes. Build the simplest correct design that expresses those elements clearly. Simple does not mean careless, temporary, or untested: every increment must remain correct, readable, maintainable, and supported by proportionate verification.

Let real usage, repeated changes, and measured bottlenecks provide evidence for later refactoring and optimization. Delaying an abstraction does not permit accumulating disorder; each iteration must leave the code clean, testable, and safe to change. Decisions that are expensive to reverse, including public contracts, persistent data, security boundaries, resource ownership, and failure semantics, require deliberate design before implementation.

### Apply Proven Design Principles

Lynxus uses the following design framework:

```text
SOLID principles
        |
        v
High cohesion and low coupling
        |
        v
Appropriate design patterns
```

SOLID guides the design of classes, interfaces, modules, and extension boundaries:

- **Single Responsibility Principle:** each module has one coherent responsibility and one clear owner for its state, invariants, and failures;
- **Open/Closed Principle:** stable behavior is extended through explicit contracts instead of repeatedly modifying the fixed execution lifecycle;
- **Liskov Substitution Principle:** an implementation that replaces another must preserve its documented behavior, resource ownership, and failure semantics;
- **Interface Segregation Principle:** callers depend on small, purpose-specific interfaces rather than broad framework abstractions;
- **Dependency Inversion Principle:** high-level policy depends on stable abstractions, while low-level compiler, JDBC, container, and tooling details remain behind those boundaries.

The desired result is high cohesion within each module and low coupling between modules. Related behavior and invariants stay together, dependencies remain explicit and one-way where possible, and internal complexity does not leak into generated Mappers or application code.

Established design patterns are tools for recurring problems, not goals by themselves. Use a pattern only when it makes ownership, collaboration, or extension clearer and reduces the concepts callers must understand. Do not add a pattern, wrapper, layer, callback, or configuration option merely to make the design appear more flexible.

Do not apply SOLID principles, design patterns, abstraction rules, or architectural styles mechanically. Use them only when they make the design easier to understand, verify, maintain, and change.

For Lynxus, this keeps the runtime contract cohesive, separates compiler and integration concerns, and allows exceptional behavior through narrow typed contracts such as `SqlProvider`, `ParameterBinder`, `RowMapper`, `ExecutionInterceptor`, `ExecutionPlugin`, and `ConnectionHandleFactory`. These extensions must not replace the fixed JDBC lifecycle or become a MyBatis-style phase chain over prepare, bind, or mapping. `ExecutionPlugin` may only observe, replace an immutable plan, or short-circuit around `SqlExecutor`.

### Keep DataSource Ownership Unambiguous

One generated Mapper is assembled against one `SqlExecutor` and one DataSource domain. Multiple DataSources use disjoint Mapper groups. Dynamic rebinding of the same Mapper to multiple DataSources is outside the contract.

Spring may provide IoC, transaction managers, physical DataSources, and ordered interceptor beans. It does not own Mapper semantics or JDBC execution.

## Compatibility Philosophy

Lynxus supports common Mapper authoring directly, converts some MyBatis patterns into static Lynxus forms, and rejects features that depend on session state, runtime interpretation, complex object graphs, or hidden framework policy.

Deterministic MyBatis JDBC value behavior is a compatibility target. Lynxus generates Java type information and uses a fixed Core `TypeHandlerManager` to combine it with optional parameter `jdbcType` declarations or live result metadata. Generated result assemblers still construct records and JavaBeans directly inside the executor lifecycle. Unsupported scalar representations use an explicit `ParameterBinder` or `RowMapper`; package mappings, database-specific routing, unknown-object fallback, reflection-based construction, global registries, and resource values that cannot survive the fixed JDBC cleanup boundary remain outside the contract.

Direct support focuses on:

- annotation and XML CRUD;
- controlled dynamic SQL;
- scalar, record, JavaBean, list, optional, cursor, batch, and generated-key contracts;
- explicit transactions and DataSource bindings;
- typed providers, binders, row mappers, interceptors, and closed-effect execution plugins.

Migration tooling may rewrite deterministic syntax. It must report rather than guess when encountering complex `resultMap` graphs, nested queries, arbitrary OGNL, plugins, caches, or ambiguous Spring configuration.

## Explicit Non-Goals

Lynxus does not add:

- first-level or second-level ORM session caches (`SqlSession` local cache or a second-level cache);
- `SqlSession`;
- runtime XML reload or OGNL interpretation;
- lazy loading or complex relationship graphs;
- automatic count queries or framework `Page<T>` models;
- distributed transaction management;
- a general SQL-rewrite plugin chain over JDBC prepare, bind, or mapping;
- full MyBatis plugin or API compatibility;
- one Mapper dynamically bound to multiple DataSources.

Opt-in `QueryCache` short-circuit and LIMIT/OFFSET replace-plan pagination are closed `ExecutionPlugin` effects, not those non-goals.

These omissions are deliberate boundaries, not incomplete features.

## Evidence Before Claims

Architecture and performance claims require executable evidence:

- public contracts are protected by focused tests and API checks;
- generated source is protected by golden and compilation tests;
- JDBC behavior is tested against PostgreSQL and MySQL with Testcontainers;
- database-free compiler, lifecycle, and wiring behavior is verified with narrow test doubles;
- Spring behavior is verified through physical DataSources and transaction managers;
- Maven and Gradle consumption is verified outside the reactor;
- benchmark claims use equivalent transaction boundaries and reproducible commands.

Optimization follows measurement. A shorter theoretical path is not a performance result.

## Change Decision Checklist

Before accepting a design change, ask:

1. Does it reduce or increase the concepts users must understand?
2. Can the behavior be decided at compile time?
3. Does it preserve the single JDBC lifecycle?
4. Does it keep runtime artifacts independent of processor and tooling code?
5. Is the extension typed and narrow, or is it becoming a general plugin mechanism?
6. Is DataSource and transaction ownership explicit?
7. Is failure and resource ownership observable and deterministic?
8. Is the change justified by a user case, compatibility need, or measured evidence?
9. Can the contract be tested through a stable public boundary?
10. Does the documentation identify one authoritative source of truth?

The preferred change is the smallest one that strengthens these invariants while keeping ordinary Mapper use simple.
