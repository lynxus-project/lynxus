---
name: lynxus
description: Plan, integrate, build, troubleshoot, or migrate a Java project with Lynxus. Use when an AI agent needs to adopt Lynxus, configure Mapper/DataSource boundaries, generate a bounded implementation TODO list, or migrate MyBatis; scan first and pause for user decisions when scope or semantics are unclear.
---

# Lynxus Agent Skill

Use this skill as the default agent workflow for Lynxus work. It covers greenfield adoption, existing-project integration, verification, and the MyBatis migration path. Keep changes incremental, reviewable, and tied to Lynxus's documented contracts.

## Operating model

Lynxus work is agent-led but user-directed. A large repository is never an excuse to guess or to rewrite everything in one pass. Inspect first, produce a bounded TODO list, complete one batch, report evidence, and ask before crossing an authorization or semantic decision gate.

## Guardrails

- Establish the project path, revision, authorization to edit, Lynxus revision, and requested output before reading or changing files outside the current workspace.
- Read the Lynxus checkout's `AGENTS.md`, `docs/README.md`, `docs/reference/core-contract.md`, `docs/reference/extensions.md`, and the relevant user guide before making contract claims.
- Preserve one Mapper → one DataSource domain. Treat package-to-DataSource bindings, Spring routing, and transaction ownership as explicit decisions.
- Keep generated Mappers on the public runtime contracts. Do not add sessions, runtime proxies, reflection fallback, runtime XML/OGNL interpretation, caches, or a general SQL rewrite chain.
- Do not publish, deploy, push, open pull requests, or change issue state unless the user explicitly authorizes that action.
- Never silently convert ambiguous SQL, result graphs, dynamic expressions, lifecycle-bound JDBC values, or transaction behavior. Record an intervention and ask.

## Workflow

### 1. Establish scope

Record:

- target project path and revision;
- whether inspection-only or source edits are authorized;
- Lynxus checkout and contract revision;
- Java/build/Spring/DataSource versions;
- requested report or TODO-list location.

If any item changes public behavior or ownership, surface the assumption before implementation.

### 2. Scan before planning

Inspect build files, source roots, Mapper interfaces, XML resources, Spring configuration, DataSource beans, tests, and generated-source directories. Prefer fast local search (`rg`) and existing project tooling. For a MyBatis project, run the bundled inventory helper:

```bash
python3 skills/lynxus/scripts/scan_mybatis.py \
  --project /path/to/project \
  --output /path/to/report/mybatis-inventory.md
```

The inventory is evidence, not a compatibility decision. Inspect each referenced declaration.

### 3. Create a bounded TODO list

Before editing, write a TODO list grouped by Mapper, package/DataSource domain, build module, or integration boundary. Preserve source paths and line ranges. Order items by dependency:

1. contract and ownership decisions;
2. build and annotation-processor wiring;
3. deterministic Mapper conversion or creation;
4. Spring/DataSource and transaction assembly;
5. focused compiler/tests;
6. module and external-consumer verification;
7. documentation and handoff.

Mark every item `ready`, `needs-user-decision`, `blocked`, or `done`. Process large projects in bounded batches. Do not start the next batch until the current batch has a recorded diff and verification result.

### 4. Choose the smallest valid path

For a new integration, start with one representative Mapper and one real execution path. For an existing Mapper, preserve SQL meaning, parameter names, result shape, statement identity, transaction behavior, and DataSource boundaries. Prefer generated Java and explicit typed extension points over runtime interpretation.

### 5. Decision gates and interaction

Pause with one concise question when progress depends on missing authorization, an ambiguous construct, unsupported behavior needing a replacement, conflicting DataSource/transaction choices, a failed compiler/test, or permission to begin another TODO batch. Record the question, answer, and affected TODO items. Continue independent ready items only when the decision cannot change their scope or semantics.

### 6. Verify in layers

Run the narrowest relevant compiler or test first, then the owning module, then the reactor or external consumer when the change crosses a module boundary. Use database-free tests for compiler and lifecycle behavior; use the project's PostgreSQL/MySQL Testcontainers checks for schema-backed execution. Treat diagnostics, generated-source differences, skipped database jobs, and dependency-boundary failures as findings.

### 7. Deliver evidence

Report the completed TODO items, changed files, exact commands and exit status, unresolved decisions, remaining risks, and the next bounded batch. For migration work, use [references/report-schema.md](references/report-schema.md).

## MyBatis migration path

When the target contains MyBatis, keep the general workflow above and add these steps:

1. Pin the MyBatis version and Lynxus contract revision.
2. Run `scripts/scan_mybatis.py` and inspect every Mapper method, XML declaration, provider, handler, plugin, cache, session, and Spring binding it identifies.
3. Classify each construct against `docs/reference/mybatis-compatibility.md` as deterministic conversion, typed/application-owned replacement, explicit rejection, or ambiguous intervention.
4. Convert only deterministic constructs or explicitly approved replacements. Do not guess about OGNL, `${...}`, nested result graphs, plugins, caches, sessions, custom language drivers, dynamic DataSource selection, or transaction semantics.
5. Compile and test each bounded batch, then update the migration report and TODO list.

The migration skill is therefore a mode of this unified `lynxus` skill, not a separate user-facing workflow.

## Completion criteria

Work is complete only when every in-scope TODO item has a classification, changed-file record (or an explicit no-change result), fresh verification evidence, and a documented remaining-risk boundary. A clean report with no source change is valid when the project needs a user decision or manual intervention.
