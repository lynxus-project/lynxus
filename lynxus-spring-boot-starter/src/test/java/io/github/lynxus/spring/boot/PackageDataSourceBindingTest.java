package io.github.lynxus.spring.boot;

import org.junit.jupiter.api.Test;
import io.github.lynxus.spring.boot.fixture.URLMapper;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.AbstractDataSource;
import org.springframework.jdbc.datasource.lookup.AbstractRoutingDataSource;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PackageDataSourceBindingTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(LynxusAutoConfiguration.class))
        .withUserConfiguration(MultipleDataSourceConfiguration.class);

    @Test
    void usesSpringBeanNameRulesForAcronymMapperNames() {
        contextRunner
            .withPropertyValues(bindings(
                "io.github.lynxus.spring.boot.fixture", "usersDataSource"))
            .run(context -> {
                assertNull(context.getStartupFailure());
                assertNotNull(context.getBean("URLMapper", URLMapper.class));
            });
    }

    @Test
    void ignoresMapperImplementationWithoutProcessorMetadata() {
        contextRunner
            .withPropertyValues(bindings(
                "io.github.lynxus.spring.boot.invalid", "usersDataSource"))
            .run(context -> {
                assertNull(context.getStartupFailure());
                assertTrue(context.getBeansOfType(Object.class).keySet().stream()
                    .noneMatch(name -> name.contains("invalidMapper")));
            });
    }

    @Test
    void mapperBindingDoesNotExposeBeanNamePrefix() {
        assertFalse(Arrays.stream(LynxusProperties.MapperBinding.class.getMethods())
            .anyMatch(method -> method.getName().equals("getBeanNamePrefix")
                || method.getName().equals("setBeanNamePrefix")));
    }

    @Test
    void failsForMissingExecutorBean() {
        contextRunner
            .withPropertyValues(bindings(
                "io.github.lynxus.spring.boot.fixture", "missingDataSource"))
            .run(context -> assertStartupFailureContains(context.getStartupFailure(), "missingDataSource"));
    }

    @Test
    void failsForOverlappingPackageRules() {
        contextRunner
            .withPropertyValues(bindings(
                "io.github.lynxus.spring.boot", "usersDataSource",
                "io.github.lynxus.spring.boot.fixture", "ordersDataSource"))
            .run(context -> assertStartupFailureContains(context.getStartupFailure(), "overlap"));
    }

    @Test
    void failsForDuplicateMapperBeanNames() {
        contextRunner
            .withPropertyValues(bindings(
                "io.github.lynxus.spring.boot.fixture", "usersDataSource",
                "io.github.lynxus.spring.boot.fixture", "ordersDataSource"))
            .run(context -> assertStartupFailureContains(context.getStartupFailure(), "overlap"));
    }

    @Test
    void requiresDataSourceNameInsteadOfChoosingAmongMultipleBeans() {
        contextRunner
            .withPropertyValues(bindings(
                "io.github.lynxus.spring.boot.fixture", ""))
            .run(context -> assertStartupFailureContains(context.getStartupFailure(), "data-source"));
    }

    private String[] bindings(String... values) {
        String[] properties = new String[values.length];
        for (int index = 0; index < values.length; index += 2) {
            int bindingIndex = index / 2;
            properties[index] = "lynxus.mapper-bindings[" + bindingIndex + "].package-name=" + values[index];
            properties[index + 1] = "lynxus.mapper-bindings[" + bindingIndex
                + "].data-source=" + values[index + 1];
        }
        return properties;
    }

    private void assertStartupFailureContains(Throwable failure, String expected) {
        assertNotNull(failure);
        Throwable current = failure;
        while (current != null) {
            if (current.getMessage() != null && current.getMessage().contains(expected)) {
                return;
            }
            current = current.getCause();
        }
        assertTrue(false, "Expected startup failure containing: " + expected);
    }

    @Configuration(proxyBeanMethods = false)
    static class MultipleDataSourceConfiguration {

        @Bean
        DataSource usersDataSource() {
            return new InertDataSource();
        }

        @Bean
        DataSource ordersDataSource() {
            return new InertDataSource();
        }

        @Bean
        DataSource userRoutingDataSource(
                @Qualifier("usersDataSource") DataSource usersDataSource) {
            AbstractRoutingDataSource routingDataSource = new AbstractRoutingDataSource() {
                @Override
                protected Object determineCurrentLookupKey() {
                    return "users";
                }
            };
            routingDataSource.setDefaultTargetDataSource(usersDataSource);
            routingDataSource.setTargetDataSources(java.util.Map.of("users", usersDataSource));
            return routingDataSource;
        }

        @Bean
        PlatformTransactionManager usersTransactionManager(
                @Qualifier("usersDataSource") DataSource usersDataSource) {
            return new DataSourceTransactionManager(usersDataSource);
        }

        @Bean
        PlatformTransactionManager ordersTransactionManager(
                @Qualifier("ordersDataSource") DataSource ordersDataSource) {
            return new DataSourceTransactionManager(ordersDataSource);
        }

    }

    private static final class InertDataSource extends AbstractDataSource {

        @Override
        public java.sql.Connection getConnection() throws SQLException {
            throw new SQLException("Database access is not expected in binding-only tests");
        }

        @Override
        public java.sql.Connection getConnection(String username, String password) throws SQLException {
            return getConnection();
        }
    }
}
