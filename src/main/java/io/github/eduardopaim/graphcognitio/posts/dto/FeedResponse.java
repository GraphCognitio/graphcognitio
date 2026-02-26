package io.github.eduardopaim.graphcognitio.posts.dto;

import java.util.List;

public record FeedResponse(
        List<PostResponse> items,
        String nextCursor
) {
}
