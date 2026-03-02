package io.github.eduardopaim.graphcognitio.posts.dto;

import java.util.List;

public record PostThreadResponse(
        List<PostResponse> ancestors,
        PostResponse focus,
        List<ThreadNodeResponse> replies
) {
}
