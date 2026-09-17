package io.github.lynxus;

import io.github.lynxus.api.ConfigurationException;
import io.github.lynxus.api.ConnectionHandleFactory;
import io.github.lynxus.api.ExecutionInterceptor;
import io.github.lynxus.api.ExecutionPlugin;
import io.github.lynxus.api.SqlExecutor;
import io.github.lynxus.api.TransactionDomain;
import io.github.lynxus.api.TransactionalExecutor;
import io.github.lynxus.jdbc.JdbcSqlExecutor;
import io.github.lynxus.transaction.SimpleTransactionDomainGuard;
import io.github.lynxus.transaction.SimpleConnectionHandleFactory;
import io.github.lynxus.transaction.SimpleTransactionalExecutor;

import javax.sql.DataSource;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * Immutable set of JDBC execution and transaction roles assembled for one DataSource domain.
 */
public final class JdbcAssembly {

    private final SqlExecutor sqlExecutor;
    private final TransactionalExecutor transactionalExecutor;

    private JdbcAssembly(SqlExecutor sqlExecutor, TransactionalExecutor transactionalExecutor) {
        this.sqlExecutor = sqlExecutor;
        this.transactionalExecutor = transactionalExecutor;
    }

    public SqlExecutor sqlExecutor() {
        return sqlExecutor;
    }

    public TransactionalExecutor transactionalExecutor() {
        return transactionalExecutor;
    }

    public static SqlExecutor sqlExecutor(
            ConnectionHandleFactory connectionHandleFactory,
            List<ExecutionInterceptor> interceptors) {
        return sqlExecutor(connectionHandleFactory, interceptors, List.of());
    }

    public static SqlExecutor sqlExecutor(
            ConnectionHandleFactory connectionHandleFactory,
            List<ExecutionInterceptor> interceptors,
            List<ExecutionPlugin> plugins) {
        return InterceptingSqlExecutor.wrap(
            new JdbcSqlExecutor(connectionHandleFactory), interceptors, plugins);
    }

    public static final class Builder {

        private final DataSource dataSource;
        private TransactionDomain domain = new TransactionDomain("default");
        private SimpleTransactionDomainGuard domainGuard = new SimpleTransactionDomainGuard();
        private List<ExecutionInterceptor> interceptors = List.of();
        private List<ExecutionPlugin> plugins = List.of();

        Builder(DataSource dataSource) {
            this.dataSource = dataSource;
        }

        public Builder domain(String key) {
            try {
                domain = new TransactionDomain(key);
            } catch (IllegalArgumentException failure) {
                throw new ConfigurationException("domain must not be blank", failure);
            }
            return this;
        }

        public Builder domainGuard(SimpleTransactionDomainGuard domainGuard) {
            if (domainGuard == null) {
                throw new ConfigurationException("domainGuard must not be null");
            }
            this.domainGuard = domainGuard;
            return this;
        }

        public Builder interceptors(List<ExecutionInterceptor> interceptors) {
            if (interceptors == null) {
                throw new ConfigurationException("interceptors must not be null");
            }

            List<ExecutionInterceptor> copy = new ArrayList<>(interceptors.size());
            Map<ExecutionInterceptor, Boolean> identities = new IdentityHashMap<>();
            for (int index = 0; index < interceptors.size(); index++) {
                ExecutionInterceptor interceptor = interceptors.get(index);
                if (interceptor == null) {
                    throw new ConfigurationException("interceptors[" + index + "] must not be null");
                }
                if (identities.put(interceptor, Boolean.TRUE) != null) {
                    throw new ConfigurationException("duplicate interceptor instance at index " + index);
                }
                copy.add(interceptor);
            }
            this.interceptors = List.copyOf(copy);
            return this;
        }

        public Builder plugins(List<ExecutionPlugin> plugins) {
            if (plugins == null) {
                throw new ConfigurationException("plugins must not be null");
            }
            List<ExecutionPlugin> copy = new ArrayList<>(plugins.size());
            Map<ExecutionPlugin, Boolean> identities = new IdentityHashMap<>();
            for (int index = 0; index < plugins.size(); index++) {
                ExecutionPlugin plugin = plugins.get(index);
                if (plugin == null) {
                    throw new ConfigurationException("plugins[" + index + "] must not be null");
                }
                if (identities.put(plugin, Boolean.TRUE) != null) {
                    throw new ConfigurationException("duplicate plugin instance at index " + index);
                }
                copy.add(plugin);
            }
            this.plugins = List.copyOf(copy);
            return this;
        }

        public JdbcAssembly build() {
            SimpleConnectionHandleFactory connectionHandleFactory = new SimpleConnectionHandleFactory(
                dataSource, domain, domainGuard);
            return new JdbcAssembly(
                sqlExecutor(connectionHandleFactory, interceptors, plugins),
                new SimpleTransactionalExecutor(connectionHandleFactory)
            );
        }
    }
}
