package io.github.lynxus.plugin;

import io.github.lynxus.api.QueryCache;
import io.github.lynxus.api.SqlResult;

import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Concurrent in-memory {@link QueryCache}. It does not evict or invalidate on writes.
 */
public final class MemoryQueryCache implements QueryCache {

    private final ConcurrentHashMap<CacheKey, SqlResult<?>> values = new ConcurrentHashMap<>();

    @Override
    public Optional<SqlResult<?>> get(String statementId, Object[] parameters) {
        return Optional.ofNullable(values.get(new CacheKey(statementId, parameters)));
    }

    @Override
    public void put(String statementId, Object[] parameters, SqlResult<?> result) {
        values.put(new CacheKey(statementId, parameters), Objects.requireNonNull(result, "result"));
    }

    private static final class CacheKey {

        private final String statementId;
        private final Object[] parameters;

        private CacheKey(String statementId, Object[] parameters) {
            this.statementId = Objects.requireNonNull(statementId, "statementId");
            this.parameters = parameters == null ? new Object[0] : parameters.clone();
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof CacheKey key)) {
                return false;
            }
            return statementId.equals(key.statementId) && Arrays.deepEquals(parameters, key.parameters);
        }

        @Override
        public int hashCode() {
            return 31 * statementId.hashCode() + Arrays.deepHashCode(parameters);
        }
    }
}
