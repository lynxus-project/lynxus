---
title: AOT-first Java ORM with GraalVM Native Image
description: Verify Lynxus's AOT-first generated Java Mapper path with GraalVM Native Image, ARM64, and explicit JDBC execution.
slug: docs/user/aot
---

# AOT Usage

Lynxus is AOT-first by design. During Java compilation, the Lynxus processor
validates the Mapper and generates an ordinary Java implementation. At runtime
the application uses that generated class, Lynxus Core, and the JDBC driver.

```text
Mapper + SQL
    -> javac + Lynxus processor
    -> generated MapperImpl
    -> Lynxus Core + JDBC
```

The generated path does not require runtime Mapper proxies, XML interpretation,
OGNL evaluation, or reflective construction for standard generated mappings.
Dynamic SQL becomes Java control flow and statement metadata is compiled into
the generated source.

## Native Image

The standalone generated Mapper path is verified with a GraalVM Native Image
smoke build. From the repository root:

```bash
lynxus-examples/native-image/verify-native-image.sh
```

The script:

1. installs the Core and processor artifacts for the external consumer;
2. compiles `NativeImageMapperImpl` through annotation processing;
3. copies only the consumer runtime dependencies;
4. builds a `linux/arm64` Native Image with `--no-fallback`;
5. runs the native executable and verifies the generated Mapper result.

The example uses the `ghcr.io/graalvm/native-image-community:21` container. Docker
is required; the script passes the configured HTTP, HTTPS, and SOCKS proxy
variables to Docker. On an Apple Silicon Mac, Docker's ARM64 Linux image runs
natively through the Linux VM. The M1/M2/M3 chip family is not a limitation.

## Dependency Boundary

`lynxus-processor` belongs on the compiler's annotation processor path. It is
not a runtime or Native Image dependency. FreeMarker is likewise build-time
only. The native executable contains generated Mapper classes, Lynxus Core, and
the application's runtime dependencies.

The smoke check proves the strongest current standalone claim: a Lynxus
generated Mapper can be compiled and executed as an ARM64 Native Image without
application-provided reflection configuration.

## Spring Boot

Spring Boot applications use Spring Boot's AOT processing in addition to
Lynxus's annotation processing. Spring Native is not a separate dependency in
the supported Spring Boot integration. Spring AOT contributes reachability
metadata for Spring-managed beans, proxies, reflection, and resources; Lynxus
continues to generate the Mapper implementation at Java compile time.

The standalone smoke check does not prove the Spring Boot Native Image path.
That path needs a separate application-level test covering the starter's bean
registration, DataSource binding, transaction participation, JDBC driver, and
any custom extensions. Applications using Spring Boot, custom extensions, or
database drivers should keep that verification as an independent Native Image
gate.
