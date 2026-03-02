package io.github.eduardopaim.graphcognitio.posts.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record PostContextSummary(
        UUID id,
        UUID rootId,
        String authorName,
        Instant createdAt,
        int likeCount,
        int replyCount,
        List<String> topicKeywords,
        double semanticScore
) {
}
