<p align="center">
  <img src="docs/assets/lynxus-logo.svg" width="360" alt="Lynxus logo">
</p>

# Lynxus

[English](README.md)

Lynxus 是一个面向 Java 21 的轻量级编译期 SQL Mapper。它在 javac 注解处理阶段读取 Mapper 接口、SQL 注解和可选 XML，生成普通 Java 实现，再通过明确、固定的 JDBC 生命周期执行不可变计划。

Lynxus 不追求完整复刻 MyBatis。它关注显式 SQL、编译期诊断、可读的生成代码、确定性的 JDBC 行为，以及不依赖 Mapper 代理和运行期 XML 解释的小型运行时。

## 顶层设计

![Lynxus 编译期与运行期架构](docs/assets/lynxus-architecture.svg)

下图对比 Lynxus 与传统 ORM 在编译期、启动期和方法调用期的生效流程：

![Lynxus 与传统 ORM 生命周期的动态对比](docs/assets/lynxus-vs-traditional-orm-flow-zh.gif)

- Mapper 校验、动态 SQL 编译、参数规划和结果映射生成发生在 javac 阶段。
- 生成的 Mapper 是普通 Java 类，只依赖一个 `SqlExecutor`。
- Standalone 与 Spring 共用同一个 `JdbcSqlExecutor` 执行生命周期。
- 一个 Mapper 只属于一个 DataSource 域。
- 标准 JDBC 值由 Core 路由，不需要数据库专用映射包。

完整模型见英文的[架构概览](docs/user/architecture.md)和[设计哲学](Design-Philosophy.md)。

## 模块

| 模块 | 职责 |
| --- | --- |
| `lynxus-core` | 运行时注解、生成 Mapper 契约、执行计划和 Standalone JDBC 运行时 |
| `lynxus-processor` | 注解处理器、SQL 编译、校验和源码生成 |
| `lynxus-spring-boot-starter` | Mapper 注册、命名 DataSource 绑定和 Spring 事务参与 |
| `lynxus-examples/basic-mapper` | 可运行的注解、XML、映射、事务、批处理和生成键示例 |
| `lynxus-benchmarks` | Direct JDBC、Lynxus 和 MyBatis 的可复现 JMH 基准 |

## 开始使用

Lynxus 在注解处理阶段读取 Mapper 接口、注解和可选 XML，并在 `target/generated-sources/annotations` 生成普通 Java 实现。

```java
import io.github.lynxus.annotation.Mapper;
import io.github.lynxus.annotation.Param;
import io.github.lynxus.annotation.Select;

record User(Long id, String name) {
}

@Mapper
interface UserMapper {

    @Select("SELECT id, name FROM users WHERE id = #{id}")
    User findById(@Param("id") Long id);
}
```

编译生成的是普通 Java 类，不是 JDK 代理。上面的 Mapper 会得到 `UserMapperImpl`。请在本地编译后查看 `target/generated-sources/annotations` 中的实文件。下面的摘录与当前处理器形状一致，是文档说明，不是检入的生成文件。

```java
public class UserMapperImpl implements UserMapper {
    private final SqlExecutor sqlExecutor;

    public UserMapperImpl(SqlExecutor sqlExecutor) {
        this.sqlExecutor = java.util.Objects.requireNonNull(sqlExecutor, "sqlExecutor");
    }

    private static final QueryDefinition<User> FIND_BY_ID_DEFINITION = QueryDefinition.assembled(
        "UserMapper.findById",
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
}
```

**compile-time mapper index**（编译期 mapper 索引）是处理器为每个生成 Mapper 写出的 mapper metadata resource 的集合。Starter 在启动时读取它，用来注册已经生成的类，而不是扫描 `*MapperImpl`。

| 阶段 | MyBatis | Lynxus |
| --- | --- | --- |
| 编译 | Mapper 接口和 XML 几乎原样进包 | javac 校验 SQL、参数、动态 SQL 和结果映射；生成普通 `MapperImpl`；写出 compile-time mapper index |
| 启动 | 解析 XML、构建 `MappedStatement`、创建 JDK 代理、扫描 Mapper | 读取 compile-time mapper index 并注册已生成的类。不解析 XML，不创建代理，不扫描 `*MapperImpl`。已在 Spring Boot 4.1.1 上验证 |
| 调用 | `SqlSession.getMapper` 代理 → 动态 SQL / OGNL → JDBC | 普通 Java 方法 → 已经编好的计划 → `SqlExecutor` → JDBC。没有运行时 XML，没有 OGNL |

完整步骤（依赖、注解处理、Standalone JDBC、Spring Boot）见英文[快速开始](docs/user/getting-started.md)。渲染后的文档在 [documentation site](https://lynxus-project.github.io/)。

```bash
mvn clean test
```

## 文档

- [用户文档](docs/user/README.md)
- [值映射与行映射](docs/user/core/mapping.md)
- [Standalone JDBC](docs/user/core/standalone.md)
- [Spring Boot 集成](docs/user/spring/spring-boot.md)
- [从 MyBatis 迁移](docs/user/migration/from-mybatis.md)
- [参考与项目文档索引](docs/README.md)

除本入口文件外，其他文档暂时只维护英文版本。稳定行为以英文的[Core 契约](docs/reference/core-contract.md)为准；进行中的规格与实现任务由 GitHub Issues 跟踪。
