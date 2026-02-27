package io.github.eduardopaim.graphcognitio.cache;

import java.util.UUID;

public final class CacheKeys {

    private CacheKeys() {
    }

    public static String feed(String cursor, int limit, UUID viewerId) {
        String normalizedCursor = cursor == null ? "" : cursor.trim();
        String normalizedViewer = viewerId == null ? "anonymous" : viewerId.toString();
        return normalizedViewer + "|" + limit + "|" + normalizedCursor;
    }

    public static String graph(UUID rootId, int depth, int limit) {
        return rootId + "|" + depth + "|" + limit;
    }
}
