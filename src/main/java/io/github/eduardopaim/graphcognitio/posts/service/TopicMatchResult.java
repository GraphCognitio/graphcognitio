package io.github.eduardopaim.graphcognitio.posts.service;

import io.github.eduardopaim.graphcognitio.repository.PostSearchProjection;
import java.util.List;

public record TopicMatchResult(
        PostSearchProjection projection,
        double topicScore,
        List<String> matchedTerms,
        String topicReason
) {
}
