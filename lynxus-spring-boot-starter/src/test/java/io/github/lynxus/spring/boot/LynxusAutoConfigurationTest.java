package io.github.lynxus.spring.boot;

import org.junit.jupiter.api.Test;
import io.github.lynxus.api.ExecutionInterceptor;
import io.github.lynxus.api.ExecutionOutcome;
import io.github.lynxus.api.ExecutionPlan;
import io.github.lynxus.api.SqlExecutor;
import io.github.lynxus.jdbc.JdbcSqlExecutor;
import io.github.lynxus.spring.boot.fixture.SpringUser;
import io.github.lynxus.spring.boot.fixture.SpringUserMapper;
import io.github.lynxus.testsupport.database.DatabaseEngine;
import io.github.lynxus.testsupport.database.TestDatabase;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

abstract class LynxusAutoConfigurationTest {

    protected abstract DatabaseEngine databaseEngine();

    @Test
    void assemblesGeneratedMapperWithOrderedJdbcExecutorInterceptors() {
        contextRunner().run(context -> {
            SqlExecutor executor = context.getBean(SqlExecutor.class);
            SpringUserMapper mapper = context.getBean(SpringUserMapper.class);
            EventLog events = context.getBean(EventLog.class);

            assertFalse(executor instanceof JdbcSqlExecutor,
                "observational interceptors must wrap SqlExecutor outside JdbcSqlExecutor");
            assertSame(mapper, context.getBean("springUserMapper"));
            assertEquals(1, mapper.insert(1L, "Alice"));
            assertEquals(new SpringUser(1L, "Alice"), mapper.findById(1L));
            assertEquals(List.of(
                "first.before", "second.before", "second.success", "first.success",
                "first.before", "second.before", "second.success", "first.success"
            ), events.values());
        });
    }

    @Test
    void generatedMapperJoinsSpringTransactionRollback() {
        contextRunner().run(context -> {
            SpringUserMapper mapper = context.getBean(SpringUserMapper.class);
            TransactionTemplate transactions = new TransactionTemplate(
                context.getBean(PlatformTransactionManager.class));

            transactions.executeWithoutResult(status -> {
                mapper.insert(2L, "Rollback");
                status.setRollbackOnly();
            });

            assertNull(mapper.findById(2L));
        });
    }

    @Test
    void singletonMapperAndExecutorSupportConcurrentCalls() {
        contextRunner().run(context -> {
            SpringUserMapper mapper = context.getBean(SpringUserMapper.class);
            List<Callable<SpringUser>> calls = new ArrayList<>();
            for (long id = 10; id < 26; id++) {
                long userId = id;
                calls.add(() -> {
                    mapper.insert(userId, "user-" + userId);
                    return mapper.findById(userId);
                });
            }

            try (var executor = Executors.newFixedThreadPool(8)) {
                List<SpringUser> users = executor.invokeAll(calls).stream()
                    .map(future -> {
                        try {
                            return future.get();
                        } catch (Exception failure) {
                            throw new AssertionError(failure);
                        }
                    })
                    .toList();

                assertEquals(16, users.size());
                for (SpringUser user : users) {
                    assertEquals("user-" + user.id(), user.name());
                }
            } catch (InterruptedException failure) {
                Thread.currentThread().interrupt();
                throw new AssertionError(failure);
            }
        });
    }

    private ApplicationContextRunner contextRunner() {
        return new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(LynxusAutoConfiguration.class))
            .withUserConfiguration(TestConfiguration.class)
            .withPropertyValues(
                "lynxus.test-database-engine=" + databaseEngine(),
                "lynxus.mapper-bindings[0].package-name=io.github.lynxus.spring.boot.fixture",
                "lynxus.mapper-bindings[0].data-source=dataSource"
            );
    }

    @Configuration(proxyBeanMethods = false)
    static class TestConfiguration {

        @Bean
        DataSource dataSource(Environment environment) throws SQLException {
            DatabaseEngine engine = DatabaseEngine.valueOf(
                environment.getRequiredProperty("lynxus.test-database-engine"));
            TestDatabase database = TestDatabase.shared(engine);
            DataSource dataSource = database.createDataSource();
            database.execute(dataSource,
                "CREATE TABLE spring_users (id BIGINT PRIMARY KEY, name VARCHAR(100))");
            return dataSource;
        }

        @Bean
        PlatformTransactionManager transactionManager(DataSource dataSource) {
            return new DataSourceTransactionManager(dataSource);
        }

        @Bean
        EventLog eventLog() {
            return new EventLog();
        }

        @Bean
        ExecutionInterceptor firstInterceptor(EventLog events) {
            return new OrderedInterceptor("first", 10, events);
        }

        @Bean
        ExecutionInterceptor secondInterceptor(EventLog events) {
            return new OrderedInterceptor("second", 20, events);
        }
    }

    static final class EventLog {

        private final List<String> events = Collections.synchronizedList(new ArrayList<>());

        void add(String event) {
            events.add(event);
        }

        List<String> values() {
            return List.copyOf(events);
        }
    }

    private record OrderedInterceptor(String name, int order, EventLog events)
            implements ExecutionInterceptor, Ordered {

        @Override
        public int getOrder() {
            return order;
        }

        @Override
        public void beforeExecution(ExecutionPlan plan) {
            events.add(name + ".before");
        }

        @Override
        public void afterSuccess(ExecutionOutcome outcome) {
            events.add(name + ".success");
        }
    }
}
