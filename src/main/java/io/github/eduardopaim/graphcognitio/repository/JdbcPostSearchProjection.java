package io.github.eduardopaim.graphcognitio.repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record JdbcPostSearchProjection(
        UUID id,
        UUID authorId,
        String content,
        Instant createdAt,
        Instant updatedAt,
        UUID parentId,
        UUID rootId,
        int replyCount,
        int likeCount,
        BigDecimal searchScore) implements PostSearchProjection {

    @Override
    public UUID getId() {
        return id;
    }

    @Override
    public UUID getAuthorId() {
        return authorId;
    }

    @Override
    public String getContent() {
        return content;
    }

    @Override
    public Instant getCreatedAt() {
        return createdAt;
    }

    @Override
    public Instant getUpdatedAt() {
        return updatedAt;
    }

    @Override
    public UUID getParentId() {
        return parentId;
    }

    @Override
    public UUID getRootId() {
        return rootId;
    }

    @Override
    public int getReplyCount() {
        return replyCount;
    }

    @Override
    public int getLikeCount() {
        return likeCount;
    }

    @Override
    public BigDecimal getSearchScore() {
        return searchScore;
    }
}
