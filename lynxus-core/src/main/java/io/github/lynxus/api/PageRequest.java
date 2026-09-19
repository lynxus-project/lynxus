package io.github.lynxus.api;

/**
 * Offset/limit coordinates for one paged SELECT. Values must be non-negative.
 */
public final class PageRequest {

    private final int offset;
    private final int limit;

    public PageRequest(int offset, int limit) {
        if (offset < 0) {
            throw new IllegalArgumentException("offset must not be negative");
        }
        if (limit < 0) {
            throw new IllegalArgumentException("limit must not be negative");
        }
        this.offset = offset;
        this.limit = limit;
    }

    public int offset() {
        return offset;
    }

    public int limit() {
        return limit;
    }
}
