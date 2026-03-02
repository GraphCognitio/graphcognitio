package io.github.eduardopaim.graphcognitio.posts.dto;

import java.util.List;

public record ContextCluster(
        String clusterId,
        List<String> topicKeywords,
        double semanticScore,
        List<PostContextSummary> highlights
) {
}
