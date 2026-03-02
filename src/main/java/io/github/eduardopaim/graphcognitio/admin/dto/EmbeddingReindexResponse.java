package io.github.eduardopaim.graphcognitio.admin.dto;

public record EmbeddingReindexResponse(
        String scope,
        int reindexedCount,
        String model,
        int dimensions
) {
}
