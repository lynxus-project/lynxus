package io.github.lynxus.api;

/**
 * Explicit per-thread holder for the {@link PageRequest} of the current Mapper call.
 * Callers must {@link #clear()} after the call. This is not an HTTP or database session.
 */
public final class PageContext {

    private static final ThreadLocal<PageRequest> CURRENT = new ThreadLocal<>();

    private PageContext() {
    }

    public static void bind(PageRequest request) {
        CURRENT.set(request);
    }

    public static PageRequest current() {
        return CURRENT.get();
    }

    public static void clear() {
        CURRENT.remove();
    }
}
