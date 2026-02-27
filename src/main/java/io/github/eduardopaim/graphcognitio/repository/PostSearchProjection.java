package io.github.eduardopaim.graphcognitio.repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public interface PostSearchProjection {

    UUID getId();

    UUID getAuthorId();

    String getContent();

    Instant getCreatedAt();

    UUID getParentId();

    UUID getRootId();

    int getReplyCount();

    int getLikeCount();

    BigDecimal getSearchScore();
}
