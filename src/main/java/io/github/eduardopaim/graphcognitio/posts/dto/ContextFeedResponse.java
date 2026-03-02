package io.github.eduardopaim.graphcognitio.posts.dto;

import java.util.List;

public record ContextFeedResponse(
        List<ContextCluster> clusters,
        String nextCursor
) {
}
