# External Spring Boot Consumer

This fixture verifies that a published-style Maven consumer can compile a
generated Lynxus Mapper alongside the Spring Boot Starter. It is independent
from the repository reactor and keeps the annotation processor on the compiler
path only.

Override `spring-boot.version` to probe a supported release anchor:

```bash
mvn -f lynxus-examples/external-spring-boot-consumer/pom.xml \
  -Dspring-boot.version=3.1.5 clean verify
```

The CI matrix uses anchors `3.1.5`, `3.5.16`, and `4.1.1`. Runtime support is
verified separately by the Starter's PostgreSQL/MySQL Testcontainers suite; an
external consumer compile alone does not establish support for an unlisted
Spring Boot line.

The fixture also contains an independent PostgreSQL Testcontainers E2E. It
starts the external Spring Boot application, verifies configuration binding and
generated Mapper injection, executes real JDBC SQL, and verifies transaction
rollback. The dedicated CI job runs this E2E against Spring Boot `4.1.1`.
