package io.github.eduardopaim.graphcognitio.graphs.dto;

import java.time.Instant;
import java.util.UUID;

public record ConversationEdgeResponse(
        UUID id,
        UUID sourcePostId,
        UUID targetPostId,
        String type,
        Instant createdAt
) {
}
