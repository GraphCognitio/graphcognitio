package io.github.eduardopaim.graphcognitio.admin.dto;

import java.time.Instant;
import java.util.UUID;

public record EmbeddingReindexHistoryEntry(
        UUID id,
        Instant createdAt,
        UUID actorUserId,
        String actorName,
        String scope,
        int reindexedCount,
        String model,
        int dimensions
) {
}
