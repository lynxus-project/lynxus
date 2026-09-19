## Explicit Non-Goals

Lynxus does not add:

- first-level, second-level, or session-scoped ORM caches;
- `SqlSession`;
- runtime XML reload or OGNL interpretation;
- lazy loading or complex relationship graphs;
- automatic count queries or framework pagination models;
- distributed transaction management;
- a general runtime SQL phase/plugin chain;
- full MyBatis plugin or API compatibility;
- one Mapper dynamically bound to multiple DataSources.

These omissions are deliberate boundaries, not incomplete features.
