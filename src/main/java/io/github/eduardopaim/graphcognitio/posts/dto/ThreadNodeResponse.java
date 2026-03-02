package io.github.eduardopaim.graphcognitio.posts.dto;

import java.util.List;

public record ThreadNodeResponse(
        PostResponse post,
        List<ThreadNodeResponse> replies
) {
}
