package io.github.lynxus.api;

import java.util.Optional;

/**
 * Stores successful SELECT {@link SqlResult} values keyed by statement id and parameter values.
 * Implementations must copy keys on write. This is not a session identity map.
 */
public interface QueryCache {

    Optional<SqlResult<?>> get(String statementId, Object[] parameters);

    void put(String statementId, Object[] parameters, SqlResult<?> result);
}
