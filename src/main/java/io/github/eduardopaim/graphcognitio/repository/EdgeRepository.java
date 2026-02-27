package io.github.eduardopaim.graphcognitio.repository;

import io.github.eduardopaim.graphcognitio.entity.EdgeEntity;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EdgeRepository extends JpaRepository<EdgeEntity, UUID> {

    List<EdgeEntity> findBySourcePostIdInOrderByCreatedAtAscIdAsc(Collection<UUID> sourcePostIds);

    void deleteBySourcePostId(UUID sourcePostId);
}
