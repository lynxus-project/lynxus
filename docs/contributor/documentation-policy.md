# Lynxus Documentation Policy

## Language

- English is required for source comments, Javadocs, diagnostics documentation, contributor documentation, architecture documents, and rule files.
- Explicit translations such as `README_cn.md` may use their target language.

## Document Types

### Project Entry Point

Root `README.md` and `README_cn.md` provide a short introduction, the top-level execution model, module inventory, and links into the documentation tree. They do not own detailed setup, extension, compatibility, or benchmark content.

### User Guide

Task-oriented documentation under `docs/user/`, grouped by product module or integration. User guides explain how to apply the contracts and must not duplicate normative tables.

### Contract

Defines behavior that code, users, or integrations may rely on. A contract must link to executable tests or verification commands.

### Guide

Explains how to use, extend, migrate, benchmark, release, or contribute. A guide must defer to contracts for normative behavior.

### Evidence

Records benchmark or compatibility results with reproducible commands, environment details, and interpretation limits.

### Active Plan

Describes unfinished work. Specifications and implementation tickets live in GitHub Issues and are not product contracts. `docs/project/roadmap.md` records strategic order only.

## Lifecycle Rules

- Add every active document to `docs/README.md` or a module README.
- Use Git history for completed implementation plans; do not keep completed plans in the active documentation tree.
- Delete architecture reviews when they become stale snapshots.
- Move durable decisions from completed plans into `Design-Philosophy.md`, a contract, or an architecture decision record before deletion.
- Do not use words such as `current`, `final`, or `latest` in a document title unless the document is maintained as a living source of truth.
- Include an exact date when a time-sensitive snapshot is necessary.

## Ownership

The Lynxus code repository is the single canonical owner of technical Markdown. The `lynxus-project.github.io` repository owns only presentation, navigation, theme, and build configuration. Its synchronized `src/content/docs/` files are generated artifacts and must never be edited or treated as a second source of truth.

- `Design-Philosophy.md` owns durable principles and non-goals.
- `docs/user/` owns task-oriented user guidance organized by module.
- `docs/reference/core-contract.md` owns runtime and Mapper contracts.
- `docs/reference/extensions.md` owns extension and Spring boundaries.
- `docs/reference/mybatis-compatibility.md` owns compatibility classification.
- `docs/user/migration/from-mybatis.md` owns manual migration guidance.
- `docs/benchmarks/` owns reproducible performance evidence.
- `docs/project/roadmap.md` owns strategic delivery order.
- GitHub Issues own active specifications, dependency edges, acceptance criteria, and implementation status.

## Information Architecture

```text
README.md / README_cn.md     short project entry points
docs/user/                   user guides grouped by module
docs/reference/              normative contracts and compatibility
docs/adr/                    durable architecture decisions
docs/benchmarks/             reproducible evidence
docs/project/                roadmap and project-level material
docs/contributor/        contributor and agent workflow
```

Only `README_cn.md` is localized for now. All files under `docs/` remain English unless a dedicated localization structure is introduced later.

## Review Checklist

Before merging documentation changes:

```bash
git diff --check
rg -n '[\p{Han}]' docs README.md 'Design-Philosophy.md' \
  --glob '!README_cn.md'
```

Do not recast `AGENTS.md` Non-Negotiable Architecture, `Design-Philosophy.md` Explicit Non-Goals, or Core §1/§9 non-ownership lists to bless a new feature. Adapter contracts belong in `docs/reference/extensions.md`. `NonNegotiableArchitectureTextTest` pins those sections.

Expected: `git diff --check` succeeds and the language scan has no output.

Also verify:

- local Markdown links resolve;
- deleted documents are no longer linked;
- examples and commands use current module and artifact names;
- normative statements have one authoritative owner;
- roadmap statements are clearly identified as planned work.
