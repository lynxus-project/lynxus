package io.github.lynxus.test.plugin;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import io.github.lynxus.Lynxus;
import io.github.lynxus.api.ExecutionPlan;
import io.github.lynxus.api.PageContext;
import io.github.lynxus.api.PageRequest;
import io.github.lynxus.api.SqlResult;
import io.github.lynxus.plugin.LimitOffsetPaginationDialect;
import io.github.lynxus.plugin.PagingExecutionPlugin;
import io.github.lynxus.testsupport.database.DatabaseEngine;
import io.github.lynxus.testsupport.database.TestDatabase;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Statement;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PagingExecutionPluginDatabaseTest {

    @AfterEach
    void clearPage() {
        PageContext.clear();
    }

    @Test
    void limitOneReturnsFirstRowOnPostgreSQL() throws Exception {
        DataSource dataSource = TestDatabase.shared(DatabaseEngine.POSTGRESQL).createDataSource();
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("DROP TABLE IF EXISTS plugin_page_users");
            statement.execute("CREATE TABLE plugin_page_users (id BIGINT PRIMARY KEY, name VARCHAR(64))");
            statement.execute("INSERT INTO plugin_page_users(id, name) VALUES (1, 'Alice'), (2, 'Bob')");
        }

        PageContext.bind(new PageRequest(0, 1));
        SqlResult<Object[]> result = rawQueryResult(Lynxus.jdbc(dataSource)
            .plugins(List.of(new PagingExecutionPlugin(new LimitOffsetPaginationDialect())))
            .build()
            .sqlExecutor()
            .execute(new ExecutionPlan(
                "test.Mapper.findAll",
                "SELECT id FROM plugin_page_users ORDER BY id",
                new Object[0],
                ExecutionPlan.StatementType.SELECT,
                ExecutionPlan.SqlSource.GENERATED)));

        assertEquals(1, result.getQueryResults().size());
        assertEquals(1L, result.getQueryResults().getFirst()[0]);
    }

    @SuppressWarnings("unchecked")
    private static SqlResult<Object[]> rawQueryResult(SqlResult<?> result) {
        return (SqlResult<Object[]>) result;
    }
}
