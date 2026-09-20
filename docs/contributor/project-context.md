# Lynxus Project Context

## Repository Purpose

Lynxus is a Java 21 compile-time Java ORM. It generates ordinary Mapper implementations during annotation processing and executes immutable plans through a fixed JDBC runtime.

## Current Modules

### `lynxus-core`

Runtime responsibilities:

- public annotations and extension contracts;
- immutable execution plans and results;
- `JdbcSqlExecutor`;
- standalone transaction support;
- built-in execution interceptors;
- fixed standard JDBC routing through `TypeHandlerManager`.

### `lynxus-processor`

Owns compiler-only code and dependencies:

- `LynxusProcessor` and `CompilePipeline`;
- annotation and XML parsing;
- dynamic SQL AST and validation;
- generated Mapper source.
- structured source modeling and processor-only FreeMarker source rendering.

### `lynxus-spring-boot-starter`

Registers generated Mapper implementations, binds Mapper packages to physical DataSources, supplies Spring-aware connection handles, and participates in Spring transactions. It must depend only on runtime core at runtime.

### `lynxus-examples/basic-mapper`

Provides executable annotation/XML, mapping, provider, binder, cursor, transaction, generated-key, batch, and pagination examples.

### `lynxus-benchmarks`

Compares Direct JDBC, Lynxus, and MyBatis under controlled JMH fixtures. Benchmark results are evidence about the measured setup, not general production latency claims.

### `lynxus-test-support`

Provides non-published PostgreSQL and MySQL Testcontainers fixtures for database-backed tests. It is consumed only with test scope and must not enter user runtime dependency trees.

## Planned Modules

- `lynxus-migration`: MyBatis scanner and deterministic rewriter.
- `lynxus-generator`: DB metadata to model/Mapper skeleton generation.

## Execution Path

```text
Mapper source
  -> lynxus-processor / LynxusProcessor
  -> CompilePipeline
  -> generated MapperImpl
  -> SqlExecutor
  -> JdbcSqlExecutor
  -> ConnectionHandleFactory
  -> JDBC
```

## Source-of-Truth Map

- Product principles: `Design-Philosophy.md`
- Core contract: `docs/reference/core-contract.md`
- Extensions and Spring boundaries: `docs/reference/extensions.md`
- Migration boundary: `docs/reference/mybatis-compatibility.md` and `docs/user/migration/from-mybatis.md`
- Strategic direction: `docs/project/roadmap.md`
- Active specifications and task sequence: GitHub Issues, configured by `docs/contributor/issue-tracker.md`
- Documentation ownership: `docs/contributor/documentation-policy.md`

## Common Commands

```bash
mvn test
mvn -pl lynxus-core test
mvn -pl lynxus-spring-boot-starter -am test
mvn -pl lynxus-examples/basic-mapper -am test
mvn -pl lynxus-benchmarks -am test
gradle -p lynxus-examples/external-gradle-processor clean build
git diff --check
scripts/verify-database-test-matrix.sh
```

Database compatibility tests use Docker and may skip locally when Docker is unavailable. Release CI must execute them without skips.

## Current Strategic Direction

The active delivery order is:

```text
correctness
  -> MyBatis deterministic JDBC type parity
  -> API freeze
  -> split processor
  -> external Maven/Gradle verification
  -> public preview
  -> migration tooling
  -> optional DB generator
```

Do not implement later roadmap tools against unstable core, XML, or artifact contracts.
