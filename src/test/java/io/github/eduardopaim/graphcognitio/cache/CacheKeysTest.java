package io.github.eduardopaim.graphcognitio.cache;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.eduardopaim.graphcognitio.graphs.GraphMode;
import io.github.eduardopaim.graphcognitio.posts.dto.FeedSort;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CacheKeysTest {

    @Test
    void feedKeyUsesViewerLimitAndCursor() {
        UUID viewerId = UUID.fromString("d6a63297-3f0f-4da4-9014-097bb6ebcb55");
        String key = CacheKeys.feed("abc", 20, viewerId, FeedSort.RECENT);

        assertEquals("d6a63297-3f0f-4da4-9014-097bb6ebcb55|recent|20|abc", key);
    }

    @Test
    void feedKeyUsesAnonymousWhenViewerMissing() {
        String key = CacheKeys.feed(null, 20, null, FeedSort.RECENT);

        assertEquals("anonymous|recent|20|", key);
    }

    @Test
    void graphKeyUsesRootModeDepthAndLimit() {
        UUID rootId = UUID.fromString("2fbf8f22-5485-4f95-a9c0-1723a49bfd7c");
        String key = CacheKeys.graph(rootId, 2, 120, GraphMode.HYBRID);

        assertEquals("2fbf8f22-5485-4f95-a9c0-1723a49bfd7c|hybrid|2|120", key);
    }
}
