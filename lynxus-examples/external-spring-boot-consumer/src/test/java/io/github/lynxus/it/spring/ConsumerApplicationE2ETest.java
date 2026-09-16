package io.github.lynxus.it.spring;

import io.github.lynxus.it.spring.mapper.ConsumerUser;
import io.github.lynxus.it.spring.mapper.ConsumerUserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

@Testcontainers
@SpringBootTest(classes = ConsumerApplication.class)
class ConsumerApplicationE2ETest {

    @Container
    static final PostgreSQLContainer<?> DATABASE =
        new PostgreSQLContainer<>("postgres:16.4-alpine");

    @Autowired
    private ConsumerUserMapper mapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", DATABASE::getJdbcUrl);
        registry.add("spring.datasource.username", DATABASE::getUsername);
        registry.add("spring.datasource.password", DATABASE::getPassword);
        registry.add("lynxus.mapper-bindings[0].package-name",
            () -> "io.github.lynxus.it.spring.mapper");
        registry.add("lynxus.mapper-bindings[0].data-source", () -> "dataSource");
    }

    @BeforeEach
    void createSchema() {
        jdbcTemplate.execute("drop table if exists users");
        jdbcTemplate.execute("create table users (id bigint primary key, name varchar(100))");
    }

    @Test
    void startsBootApplicationAndExecutesGeneratedMapper() {
        assertEquals(1, mapper.insert(1L, "Alice"));
        assertEquals(new ConsumerUser(1L, "Alice"), mapper.findById(1L));
    }

    @Test
    void generatedMapperParticipatesInSpringTransactionRollback() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            mapper.insert(2L, "Rollback");
            status.setRollbackOnly();
        });

        assertNull(mapper.findById(2L));
    }
}
