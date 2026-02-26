package io.github.eduardopaim.graphcognitio.graphs.dto;

import java.time.Instant;
import java.util.UUID;

public record ConversationNodeResponse(
        UUID id,
        UUID authorId,
        String authorName,
        String contentPreview,
        Instant createdAt,
        UUID parentId,
        UUID rootId,
        int replyCount
) {
}
