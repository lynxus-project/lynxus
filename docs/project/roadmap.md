# Lynxus Roadmap

This document owns strategic delivery order. It does not track implementation steps, assignees, dependencies, or completion state. GitHub Issues own active specifications and tracer-bullet tickets; contracts, ADRs, and `CONTEXT.md` own durable facts.

## Completed Foundation

- One final JDBC execution outcome after executor-owned cleanup, separate from transaction completion.
- Deterministic compiler rejection, method-overload validation, and fail-closed offline XML parsing.
- A frozen current JDBC matrix including UUID, LocalTime, and OffsetDateTime.
- PostgreSQL 16.4 and MySQL 8.4 Testcontainers coverage for database-backed Core, Spring, and example behavior.
- H2 isolated to the controlled JMH benchmark fixture.

## Public Release Priorities

### MyBatis JDBC Type Parity

Use MyBatis 3.5.19 deterministic built-in TypeHandlers as the compatibility baseline while preserving compile-time object construction. Keep standard JDBC routing fixed in Core and database-independent; unsupported writes use `ParameterBinder`, while unsupported reads and lifecycle-bound values use `RowMapper` or raw JDBC. Complete missing structured and lifecycle-safe JDBC behavior without package mappings, database-specific artifacts, runtime discovery, unknown-object fallback, or a mutable registry.

### Public API And Artifact Boundaries

Freeze the supported public API with binary compatibility checks. Keep annotation processing in
`lynxus-processor` so runtime consumers do not carry compiler implementation or template
dependencies. Within the processor, keep compiler semantics in Java and use FreeMarker only to
render the structured generated-source layout.

### External Consumption And Spring Support

Verify Maven and Gradle consumers outside the reactor. Keep Spring limited to bean assembly, DataSource binding, and transaction participation, and maintain explicit supported-version gates.

### Release Engineering

Complete artifact metadata, license and repository checks, reproducible release workflows, published documentation, and zero-skip PostgreSQL/MySQL release gates.

## Migration And XML Priorities

- Publish an immutable Lynxus Mapper DTD with offline resolution.
- Complete the controlled XML subset, including integrity diagnostics and explicitly supported statement attributes.
- Maintain executable annotation, XML, standalone, Spring, multi-DataSource, and MyBatis migration examples.
- Build a read-only MyBatis compatibility scanner before any deterministic safe rewriter.
- Validate migration tooling against licensed external projects before claiming broad compatibility.

## Next MyBatis Compatibility Issue

The current executor and compiler provide a deterministic MyBatis-shaped subset, not a runtime
replacement for MyBatis. The next compatibility issue should measure and prioritize the remaining
gap without weakening Lynxus's fixed JDBC ownership:

- close the remaining deterministic JDBC and type-handler parity gaps;
- complete the controlled XML and dynamic-SQL compatibility classification;
- decide whether nested `resultMap` graphs are supported through explicit conversion or remain a
  migration boundary;
- preserve the explicit non-goals for `SqlSession`, session cache, lazy loading, automatic count,
  framework pagination, and the full MyBatis plugin runtime;
- produce migration-scanner evidence and external compatibility fixtures before claiming broader
  MyBatis coverage.

## Optional Tooling

A DB-to-Mapper generator may ship on an independent version track after Core, processor, XML, and artifact contracts stabilize. New ecosystem features require demonstrated user demand, a named owner, an executable contract, and a design that preserves Lynxus's fixed runtime boundaries.
