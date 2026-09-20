<p align="center">
  <img src="assets/lynxus-logo.svg" width="360" alt="Lynxus logo">
</p>

# Lynxus Documentation

This is the canonical documentation index. User-facing guides are grouped by product area under `docs/user/`; normative contracts, project records, and contributor material remain separate so readers can distinguish guidance from guarantees and internal workflow.

## User Documentation

### Start Here

- [`user/README.md`](user/README.md): user documentation map.
- [`user/getting-started.md`](user/getting-started.md): installation, annotation processing, the first Mapper, and runtime assembly.
- [`user/architecture.md`](user/architecture.md): compile-time and runtime architecture.
- [`user/aot.md`](user/aot.md): Lynxus AOT-first compilation and Native Image verification.
- [`contribute.md`](contribute.md): contributor workflow, expectations, and AI-agent contribution guidance.

### Core

- [`user/core/README.md`](user/core/README.md): Core guide index.
- [`user/core/mapping.md`](user/core/mapping.md): standard JDBC routing, generated result mapping, parameter binders, and row mappers.
- [`user/core/extensions.md`](user/core/extensions.md): decision guide for typed extension points and raw JDBC.
- [`user/core/standalone.md`](user/core/standalone.md): standalone JDBC assembly and callback transactions.

### Integrations

- [`user/spring/README.md`](user/spring/README.md): Spring guide index.
- [`user/spring/spring-boot.md`](user/spring/spring-boot.md): Mapper registration, named DataSource binding, and Spring transactions.

### Migration

- [`user/migration/README.md`](user/migration/README.md): migration guide index.
- [`user/migration/from-mybatis.md`](user/migration/from-mybatis.md): manual migration from supported MyBatis patterns.
- [`user/migration/using-migration-skill.md`](user/migration/using-migration-skill.md): install and invoke the migration skill.

## Reference

- [`reference/core-contract.md`](reference/core-contract.md): supported Mapper, JDBC, failure, transaction, concurrency, and database-mapping behavior.
- [`reference/extensions.md`](reference/extensions.md): exact Spring and typed extension contracts.
- [`reference/mybatis-compatibility.md`](reference/mybatis-compatibility.md): supported, partial, and unsupported MyBatis behavior.
- [`../Design-Philosophy.md`](../Design-Philosophy.md): durable product principles and non-goals.
- [`../CONTEXT.md`](../CONTEXT.md): canonical project terminology and ownership.

The runnable Native Image consumer is [`../lynxus-examples/native-image`](../lynxus-examples/native-image).

## Architecture Decisions and Evidence

- [`adr/0001-separate-execution-outcome-from-transaction-completion.md`](adr/0001-separate-execution-outcome-from-transaction-completion.md)
- [`adr/0002-keep-standard-jdbc-routing-fixed-in-core.md`](adr/0002-keep-standard-jdbc-routing-fixed-in-core.md): fixed Core JDBC routing and explicit binder/row-mapper escape hatches.
- [`adr/0003-call-lynxus-a-compile-time-java-orm.md`](adr/0003-call-lynxus-a-compile-time-java-orm.md): public identity is a compile-time Java ORM; the comparison baseline is MyBatis, not Hibernate.
- [`adr/0004-wrap-sql-executor-with-a-plugin-chain.md`](adr/0004-wrap-sql-executor-with-a-plugin-chain.md): executor plugins wrap `SqlExecutor`; `JdbcSqlExecutor` stays JDBC-only.
- [`benchmarks/core-ga-baseline.md`](benchmarks/core-ga-baseline.md): reproducible Core GA benchmark evidence.

The documentation site surfaces a compact MySQL benchmark snapshot directly
below its “Why teams choose Lynxus” capability overview; the benchmark report
above remains the canonical source for the full table, environment metadata,
and reproduction protocol.

## Project

- [`project/roadmap.md`](project/roadmap.md): strategic delivery order. GitHub Issues own specifications and implementation status.
- [`project/release.md`](project/release.md): reproducible release gates, signing, rollback, and governance.

## Contributor and Agent Documentation

- [`contributor/project-context.md`](contributor/project-context.md): repository context and module responsibilities.
- [`contributor/engineering-guide.md`](contributor/engineering-guide.md): engineering, testing, documentation, and commit conventions.
- [`contributor/documentation-policy.md`](contributor/documentation-policy.md): document types, ownership, and verification.
- [`contributor/tooling.md`](contributor/tooling.md): cross-agent workflow and rule discovery.
- [`contributor/issue-tracker.md`](contributor/issue-tracker.md): GitHub Issues conventions.
- [`contributor/triage-labels.md`](contributor/triage-labels.md): triage roles and labels.
- [`contributor/domain.md`](contributor/domain.md): domain-document workflow.

## Documentation Rules

- Technical Markdown has one owner: this repository. The GitHub Pages repository only builds a generated mirror for presentation and search; never edit its synchronized docs directly.
- Root `README.md` and `README_cn.md` are short project entry points; only the Chinese root README is localized for now.
- User guides explain tasks and defer to reference contracts for normative behavior.
- Reference documents own stable guarantees and compatibility classifications.
- ADRs explain durable decisions; benchmarks record reproducible evidence.
- Contributor and agent documents describe repository workflow, not user-facing product behavior.
- Active specifications and delivery state belong in GitHub Issues.
- Every active document must be linked from this index or from a module README.
