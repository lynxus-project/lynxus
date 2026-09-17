---
title: Using The Lynxus Skill
description: Install the Lynxus agent skill to scan a project, configure compile-time Mappers, and migrate from MyBatis in reviewable batches.
slug: docs/user/migration/using-migration-skill
---

# Using The Lynxus Skill

Lynxus's agent workflow is distributed as one skill. It covers greenfield
adoption, integration, verification, and MyBatis migration. It is separate
from `lynxus-core`, the annotation processor, and application Maven or
Gradle dependencies.

## Install

Copy the directory
`skills/lynxus/` into the skill directory supported by the
agent client. For Codex, a user-local installation is typically:

```bash
cp -R skills/lynxus \
  "${CODEX_HOME:-$HOME/.codex}/skills/lynxus"
```

The skill has no Lynxus runtime dependency. Keep the source checkout available
when running it so the skill can read the authoritative compatibility and
runtime contracts.

## Invoke

Ask the agent to use `$lynxus` and provide:

- the MyBatis project path or an already authorized checkout;
- the target revision and MyBatis version;
- the Lynxus checkout or contract revision;
- whether source edits are authorized;
- the desired report location.

Example:

```text
Use $lynxus on /path/to/mybatis-app.
Inspect only first; use Lynxus at /path/to/lynxus, target MyBatis 3.5.19,
and write the inventory and intervention report to /tmp/lynxus-report.
```

The skill may produce a diff only after the user authorizes edits. It never
publishes, deploys, pushes, changes issue state, or mutates an external system.
Unsupported and ambiguous constructs remain in the intervention report for
human review. For a large project, it first produces a bounded migration TODO
list, works in reviewable batches, and pauses for user decisions when an item
needs authorization, semantic clarification, or a replacement design. The
worklist and interaction decisions remain part of the report.

## Offline Inventory

The bundled inventory helper can be run independently:

```bash
python3 skills/lynxus/scripts/scan_mybatis.py \
  --project /path/to/mybatis-app \
  --output /tmp/lynxus-report/mybatis-inventory.md
```

It reads only local source and build files. Its output is a list of signals, not
a compatibility decision; the agent must inspect each declaration against the
authoritative matrix.
