package io.github.eduardopaim.graphcognitio.cache;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class CacheKeysTest {

    @Test
    void feedKeyUsesViewerLimitAndCursor() {
        UUID viewerId = UUID.fromString("d6a63297-3f0f-4da4-9014-097bb6ebcb55");
        String key = CacheKeys.feed("abc", 20, viewerId);

        assertEquals("d6a63297-3f0f-4da4-9014-097bb6ebcb55|20|abc", key);
    }

    @Test
    void feedKeyUsesAnonymousWhenViewerMissing() {
        String key = CacheKeys.feed(null, 20, null);

        assertEquals("anonymous|20|", key);
    }

    @Test
    void graphKeyUsesRootDepthAndLimit() {
        UUID rootId = UUID.fromString("2fbf8f22-5485-4f95-a9c0-1723a49bfd7c");
        String key = CacheKeys.graph(rootId, 2, 120);

        assertEquals("2fbf8f22-5485-4f95-a9c0-1723a49bfd7c|2|120", key);
    }
}
