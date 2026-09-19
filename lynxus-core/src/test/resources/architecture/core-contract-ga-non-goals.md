## 9. Explicit Non-Goals For Core GA

Core GA does not promise:

- arbitrary OGNL or complete MyBatis XML compatibility;
- complex `resultMap` graphs, nested collections, lazy loading, or session-scoped/second-level cache;
- runtime Mapper proxies, runtime XML reload, or reflection-based dispatch;
- same-Mapper multi-DataSource binding;
- automatic count queries, framework pagination models, or a pagination DSL;
- automatic retries after `OUTCOME_UNKNOWN`;
- connection pooling, distributed transactions, or production transaction policy;
- undocumented compiler implementation classes or JDBC phase interceptors as public extension APIs.

Future capabilities must preserve these ownership boundaries or update this contract with executable tests.
