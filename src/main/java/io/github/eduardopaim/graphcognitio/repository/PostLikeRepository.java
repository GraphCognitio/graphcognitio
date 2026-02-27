package io.github.eduardopaim.graphcognitio.repository;

import io.github.eduardopaim.graphcognitio.entity.PostLikeEntity;
import java.util.Collection;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PostLikeRepository extends JpaRepository<PostLikeEntity, UUID> {

    boolean existsByPostIdAndUserId(UUID postId, UUID userId);

    Optional<PostLikeEntity> findByPostIdAndUserId(UUID postId, UUID userId);

    @Query("""
            SELECT pl.postId
            FROM PostLikeEntity pl
            WHERE pl.userId = :userId
              AND pl.postId IN :postIds
            """)
    Set<UUID> findLikedPostIdsByUserIdAndPostIdIn(@Param("userId") UUID userId,
                                                   @Param("postIds") Collection<UUID> postIds);
}
