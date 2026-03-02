package io.github.eduardopaim.graphcognitio.posts.dto;

import io.github.eduardopaim.graphcognitio.entity.UserRole;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record PostResponse(
        UUID id,
        UUID authorId,
        String authorName,
        String authorUsername,
        UserRole authorRole,
        String content,
        Instant createdAt,
        Instant updatedAt,
        UUID parentId,
        UUID rootId,
        int replyCount,
        int likeCount,
        boolean likedByMe,
        List<String> topicKeywords,
        Double relevanceScore,
        List<String> relevanceSignals) {

    public PostResponse {
        topicKeywords = topicKeywords == null ? List.of() : List.copyOf(topicKeywords);
        relevanceSignals = relevanceSignals == null ? List.of() : List.copyOf(relevanceSignals);
    }

    public PostResponse(UUID id,
            UUID authorId,
            String authorName,
            String authorUsername,
            UserRole authorRole,
            String content,
            Instant createdAt,
            Instant updatedAt,
            UUID parentId,
            UUID rootId,
            int replyCount,
            int likeCount,
            boolean likedByMe) {
        this(id, authorId, authorName, authorUsername, authorRole, content, createdAt, updatedAt, parentId, rootId,
                replyCount,
                likeCount, likedByMe, List.of(), null, List.of());
    }

    public PostResponse(UUID id,
            UUID authorId,
            String authorName,
            String authorUsername,
            UserRole authorRole,
            String content,
            Instant createdAt,
            Instant updatedAt,
            UUID parentId,
            UUID rootId,
            int replyCount,
            int likeCount,
            boolean likedByMe,
            List<String> topicKeywords) {
        this(id, authorId, authorName, authorUsername, authorRole, content, createdAt, updatedAt, parentId, rootId,
                replyCount,
                likeCount, likedByMe, topicKeywords, null, List.of());
    }
}
