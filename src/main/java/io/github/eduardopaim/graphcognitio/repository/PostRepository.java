package io.github.eduardopaim.graphcognitio.repository;

import io.github.eduardopaim.graphcognitio.entity.PostEntity;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PostRepository extends JpaRepository<PostEntity, UUID> {
}
