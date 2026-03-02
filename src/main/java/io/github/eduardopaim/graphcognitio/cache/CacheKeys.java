package io.github.eduardopaim.graphcognitio.cache;

import io.github.eduardopaim.graphcognitio.graphs.GraphMode;
import io.github.eduardopaim.graphcognitio.posts.dto.FeedSort;
import java.util.UUID;

public final class CacheKeys {

    private CacheKeys() {
    }

    public static String feed(String cursor, int limit, UUID viewerId, FeedSort sort) {
        String normalizedCursor = cursor == null ? "" : cursor.trim();
        String normalizedViewer = viewerId == null ? "anonymous" : viewerId.toString();
        String normalizedSort = sort == null ? FeedSort.RECENT.queryValue() : sort.queryValue();
        return normalizedViewer + "|" + normalizedSort + "|" + limit + "|" + normalizedCursor;
    }

    public static String graph(UUID rootId, int depth, int limit, GraphMode mode) {
        String normalizedMode = mode == null ? GraphMode.CONVERSATION.queryValue() : mode.queryValue();
        return rootId + "|" + normalizedMode + "|" + depth + "|" + limit;
    }
}
