package io.github.eduardopaim.graphcognitio.graphs.dto;

import io.github.eduardopaim.graphcognitio.entity.UserRole;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ConversationNodeResponse(
        UUID id,
        UUID authorId,
        String authorName,
        UserRole authorRole,
        String contentPreview,
        Instant createdAt,
        UUID parentId,
        UUID rootId,
        int replyCount,
        Double topicScore,
        List<String> topicKeywords,
        String topicReason
) {
}
