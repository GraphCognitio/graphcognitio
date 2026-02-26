package io.github.eduardopaim.graphcognitio.posts.dto;

import java.time.Instant;
import java.util.UUID;

public record PostResponse(
        UUID id,
        UUID authorId,
        String authorName,
        String content,
        Instant createdAt,
        UUID parentId,
        UUID rootId,
        int replyCount
) {
}
