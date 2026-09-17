---
title: Contribute to Lynxus
description: Contribute to Lynxus contracts, compiler, documentation, and agent workflow with focused pull requests and executable tests.
slug: docs/contribute
---

# Contribute to Lynxus

Lynxus is in its early stage. We are building a new generation of explicit, compile-time Java ORM tooling and welcome people who want to shape its contracts, implementation, documentation, examples, and agent workflows.

## Contribution workflow

1. Read [AGENTS.md](../AGENTS.md), [Design Philosophy](../Design-Philosophy.md), and the relevant contract or module guide.
2. Search existing Issues and Pull Requests before opening a new proposal.
3. For a bug, reproduce it with the narrowest focused test. For a feature, describe the public contract and its ownership boundary first.
4. Create a focused branch from `main` and keep the diff reviewable. Do not mix unrelated cleanup into the change.
5. Run the narrowest test, the owning module checks, and documentation/link checks that cover the change.
6. Open a Pull Request using the template. Explain compatibility, verification commands, and remaining risks.
7. Respond to review feedback with follow-up commits and keep the original intent visible.

AI agents are welcome contributors. Use the [`lynxus` skill](../skills/lynxus/SKILL.md) to scan a repository, produce a bounded TODO list, work in reviewable batches, and ask before making semantic or authorization decisions.

## Good first contributions

- Improve a user guide or example while preserving the documented contract.
- Add a focused compiler, lifecycle, Spring wiring, or external-consumer test.
- Improve generated-source diagnostics or documentation for an existing behavior.
- Add a bounded example for a supported JDBC or DataSource scenario.
- Review an Issue specification for ambiguity, ownership, or missing verification evidence.

## Engineering expectations

- Keep runtime artifacts independent of the processor and template engine.
- Preserve the fixed JDBC lifecycle and explicit DataSource ownership.
- Prefer a narrow typed extension over a general runtime plugin mechanism.
- Write source comments, Javadocs, diagnostics, and commit messages in English.
- Do not claim a database or release gate passed without fresh command output.

## Communication

Use the issue templates for reproducible bugs and scoped proposals. Security reports should not be filed publicly; contact the maintainers privately through the repository's security channel.
