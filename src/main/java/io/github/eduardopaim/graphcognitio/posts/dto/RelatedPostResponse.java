package io.github.eduardopaim.graphcognitio.posts.dto;

import io.github.eduardopaim.graphcognitio.entity.UserRole;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record RelatedPostResponse(
                UUID id,
                UUID authorId,
                String authorName,
                UserRole authorRole,
                String content,
                Instant createdAt,
                Instant updatedAt,
                UUID parentId,
                UUID rootId,
                int replyCount,
                int likeCount,
                boolean likedByMe,
                double topicScore,
                List<String> topicKeywords,
                String topicReason) {
}
