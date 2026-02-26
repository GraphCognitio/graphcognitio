package io.github.eduardopaim.graphcognitio.repository;

import io.github.eduardopaim.graphcognitio.entity.PostEntity;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Pageable;

public interface PostRepository extends JpaRepository<PostEntity, UUID> {

    @Query("""
            SELECT p
            FROM PostEntity p
            WHERE p.parentId IS NULL
            ORDER BY p.createdAt DESC, p.id DESC
            """)
    List<PostEntity> findRootPostsWithoutCursor(Pageable pageable);

    @Query(value = """
            SELECT p.*
            FROM posts p
            WHERE p.parent_id IS NULL
              AND (
                    p.created_at < :cursorCreatedAt
                    OR (p.created_at = :cursorCreatedAt AND p.id < :cursorId)
              )
            ORDER BY p.created_at DESC, p.id DESC
            """, nativeQuery = true)
    List<PostEntity> findRootPostsAfterCursor(@Param("cursorCreatedAt") Instant cursorCreatedAt,
                                              @Param("cursorId") UUID cursorId,
                                              Pageable pageable);
}
