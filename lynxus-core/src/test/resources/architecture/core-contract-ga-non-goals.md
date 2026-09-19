## 9. Explicit Non-Goals For Core GA

Core GA does not promise:

- arbitrary OGNL or complete MyBatis XML compatibility;
- complex `resultMap` graphs, nested collections, lazy loading, or second-level cache;
- runtime Mapper proxies, runtime XML reload, or reflection-based dispatch;
- same-Mapper multi-DataSource binding;
- built-in pagination plugins or a pagination DSL;
- automatic retries after `OUTCOME_UNKNOWN`;
- connection pooling, distributed transactions, or production transaction policy;
- undocumented compiler implementation classes as public extension APIs.

Future capabilities must preserve these ownership boundaries or update this contract with executable tests.
