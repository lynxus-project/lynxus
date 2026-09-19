package io.github.lynxus.api;

import java.util.Optional;

/**
 * Stores successful SELECT {@link SqlResult} values keyed by an execution identity and parameter
 * values. The built-in adapter includes the statement's final SQL and active page coordinates in
 * that identity. Implementations must copy keys on write. This is not a session identity map.
 */
public interface QueryCache {

    Optional<SqlResult<?>> get(String statementId, Object[] parameters);

    void put(String statementId, Object[] parameters, SqlResult<?> result);
}
