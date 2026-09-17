---
title: Quick Start for a Compile-time Java ORM
description: Add Lynxus to Maven, generate a Mapper implementation at compile time, and run it with Spring Boot or standalone JDBC.
slug: docs/user/getting-started
---

# Quick Start

Lynxus requires Java 21. It generates ordinary Java Mapper implementations during annotation processing and executes them through a small JDBC runtime.

## 0. Install the Lynxus Skill (recommended)

Lynxus is designed for agent-led development. Install the unified `lynxus` skill before starting so your agent can scan the project, create a bounded TODO list, configure Lynxus incrementally, and switch into migration mode when it finds an existing MyBatis project. The recommended commands download the small GitHub Release artifact rather than the full Lynxus source tree; each is a single copy-paste line. Pin a versioned Release URL when reproducibility matters.

Codex:

```bash
tmp=$(mktemp -d) && curl -fsSL https://github.com/lynxus-project/lynxus/releases/latest/download/lynxus-skill.tar.gz | tar -xz -C "$tmp" && mkdir -p "${CODEX_HOME:-$HOME/.codex}/skills" && cp -R "$tmp/lynxus-skill" "${CODEX_HOME:-$HOME/.codex}/skills/lynxus"
```

Claude Code:

```bash
tmp=$(mktemp -d) && curl -fsSL https://github.com/lynxus-project/lynxus/releases/latest/download/lynxus-skill.tar.gz | tar -xz -C "$tmp" && mkdir -p "$HOME/.claude/skills" && cp -R "$tmp/lynxus-skill" "$HOME/.claude/skills/lynxus"
```

Cursor (project-local):

```bash
tmp=$(mktemp -d) && curl -fsSL https://github.com/lynxus-project/lynxus/releases/latest/download/lynxus-skill.tar.gz | tar -xz -C "$tmp" && mkdir -p .cursor/skills && cp -R "$tmp/lynxus-skill" .cursor/skills/lynxus
```

If `curl` is unavailable, use this `wget` one-liner for Codex:

```bash
tmp=$(mktemp -d) && wget -qO "$tmp/lynxus-skill.tar.gz" https://github.com/lynxus-project/lynxus/releases/latest/download/lynxus-skill.tar.gz && tar -xzf "$tmp/lynxus-skill.tar.gz" -C "$tmp" && mkdir -p "${CODEX_HOME:-$HOME/.codex}/skills" && cp -R "$tmp/lynxus-skill" "${CODEX_HOME:-$HOME/.codex}/skills/lynxus"
```

From a local checkout, the equivalent one-liner is:

```bash
tmp=$(mktemp -d) && git clone --depth 1 https://github.com/lynxus-project/lynxus.git "$tmp/lynxus" >/dev/null && mkdir -p "${CODEX_HOME:-$HOME/.codex}/skills" && cp -R "$tmp/lynxus/skills/lynxus" "${CODEX_HOME:-$HOME/.codex}/skills/lynxus"
```

Then ask your agent to use `$lynxus`. The same skill covers new integrations and MyBatis migration; it scans first, writes a reviewable TODO list, and pauses when a decision needs you.

## 1. Add the Dependency

For Spring Boot:

```xml
<dependency>
    <groupId>io.github.lynxus</groupId>
    <artifactId>lynxus-spring-boot-starter</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

For standalone JDBC, depend on `lynxus-core` and add `lynxus-processor` to the compiler's
annotation processor path. While working from this repository, install snapshots locally first:

```bash
mvn -DskipTests install
```

Explicitly enable the processor with Maven:

```xml
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-compiler-plugin</artifactId>
    <configuration>
        <proc>full</proc>
        <annotationProcessorPaths>
            <path>
                <groupId>io.github.lynxus</groupId>
                <artifactId>lynxus-processor</artifactId>
                <version>1.0.0-SNAPSHOT</version>
            </path>
        </annotationProcessorPaths>
        <annotationProcessors>
            <annotationProcessor>io.github.lynxus.compile.LynxusProcessor</annotationProcessor>
        </annotationProcessors>
    </configuration>
</plugin>
```

## 2. Define a Mapper

```java
package com.example.user.mapper;

public record User(Long id, String name) {
}
```

```java
package com.example.user.mapper;

import io.github.lynxus.annotation.Insert;
import io.github.lynxus.annotation.Mapper;
import io.github.lynxus.annotation.Param;
import io.github.lynxus.annotation.Select;

import java.util.List;

@Mapper
public interface UserMapper {

    @Insert("INSERT INTO users (id, name) VALUES (#{id}, #{name})")
    int insert(@Param("id") Long id, @Param("name") String name);

    @Select("SELECT id, name FROM users WHERE id = #{id}")
    User findById(@Param("id") Long id);

    @Select({
        "<script>",
        "SELECT id, name FROM users WHERE 1=1",
        "<if test='name != null'>AND name LIKE #{name}</if>",
        "</script>"
    })
    List<User> findByName(@Param("name") String name);
}
```

Compilation generates `UserMapperImpl` under `target/generated-sources/annotations`. The implementation directly implements `UserMapper` and receives one `SqlExecutor` through its constructor. Inspect that directory after you compile. The excerpts below follow the current processor shape; they are documentation copy, not checked-in generated files.

Static SQL becomes a `QueryDefinition` and an ordinary method that calls `SqlExecutor`:

```java
private static final QueryDefinition<User> FIND_BY_ID_DEFINITION = QueryDefinition.assembled(
    "com.example.user.mapper.UserMapper.findById",
    "SELECT id, name FROM users WHERE id = ?",
    ExecutionPlan.SqlSource.ANNOTATION,
    row -> new User((Long) row.get(0), (String) row.get(1)),
    /* parameter binders, statement options, type routing */);

@Override
public User findById(Long id) {
    QueryExecutionPlan<User> executionPlan = buildFindByIdExecutionPlan(id);
    QueryResult<User> executionResult = sqlExecutor.query(executionPlan);
    return executionResult.oneOrNull();
}

private QueryExecutionPlan<User> buildFindByIdExecutionPlan(Long id) {
    return FIND_BY_ID_DEFINITION.bind(id);
}
```

Supported dynamic SQL becomes Java control flow, not OGNL evaluation at invoke time:

```java
BoundSqlBuilder sql = BoundSqlBuilder.create("com.example.user.mapper.UserMapper#findByName");
sql.append("SELECT id, name FROM users WHERE 1=1");
if (name != null) {
    sql.append("AND name LIKE ?");
    sql.parameter(name, null, java.lang.String.class, java.sql.JDBCType.VARCHAR);
}
return FIND_BY_NAME_DEFINITION.bind(sql.build());
```

## 3. Assemble the Mapper

For Spring Boot, bind the Mapper package to a named DataSource:

```yaml
lynxus:
  mapper-bindings:
    - package-name: com.example.user.mapper
      data-source: dataSource
```

For standalone use, create a [JDBC assembly](core/standalone.md) and construct the generated implementation directly.

## Next Steps

- Read the [architecture overview](architecture.md).
- Choose a [value or row mapping strategy](core/mapping.md).
- Configure the [Spring Boot integration](spring/spring-boot.md).
- Run the [basic Mapper example](../../lynxus-examples/basic-mapper/README.md).
