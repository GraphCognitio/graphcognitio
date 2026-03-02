package io.github.eduardopaim.graphcognitio.admin.dto;

public record EmbeddingsStatusResponse(
        String status,
        boolean enabled,
        String provider,
        String model,
        int dimensions,
        int reindexBatchSize,
        boolean remoteProvider,
        Boolean apiKeyConfigured,
        String remoteBaseUrl,
        String remotePath,
        String reason
) {
}
