package io.github.eduardopaim.graphcognitio.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "edges")
public class EdgeEntity {

    @Id
    private UUID id;

    @Column(name = "source_post_id", nullable = false)
    private UUID sourcePostId;

    @Column(name = "target_post_id", nullable = false)
    private UUID targetPostId;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 20)
    private EdgeType type;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public EdgeEntity() {
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getSourcePostId() {
        return sourcePostId;
    }

    public void setSourcePostId(UUID sourcePostId) {
        this.sourcePostId = sourcePostId;
    }

    public UUID getTargetPostId() {
        return targetPostId;
    }

    public void setTargetPostId(UUID targetPostId) {
        this.targetPostId = targetPostId;
    }

    public EdgeType getType() {
        return type;
    }

    public void setType(EdgeType type) {
        this.type = type;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    @PrePersist
    void onCreate() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
