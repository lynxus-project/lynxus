# Call Lynxus a compile-time Java ORM and compare it with MyBatis

Lynxus's public identity is a compile-time Java ORM, not a mapper tool and not a Hibernate or JPA Session ORM. Mapper remains the authoring unit: interfaces, XML, and generated implementations. The competitive and compatibility baseline is MyBatis 3.5.x deterministic mapping behavior. Hibernate is a market name only, not the comparison target or the feature template.

The compile-time path is the product, not a subset. Runtime XML, OGNL, Mapper proxies, and SQL-rewrite plugin chains are out of identity, not deferred MyBatis parity. Later ORM completeness (pagination, cache, explicit association loading, statement options, Map results) must stay on generated Java and JDBC.
