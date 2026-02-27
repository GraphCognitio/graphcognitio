package io.github.eduardopaim.graphcognitio.repository;

import io.github.eduardopaim.graphcognitio.entity.PostEntity;
import java.time.Instant;
import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PostRepository extends JpaRepository<PostEntity, UUID> {

    Optional<PostEntity> findByIdAndParentIdIsNull(UUID id);

    List<PostEntity> findByParentIdInOrderByCreatedAtAscIdAsc(Collection<UUID> parentIds);

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

    @Query(value = """
            WITH ranked_posts AS (
                SELECT
                    p.id AS "id",
                    p.author_id AS "authorId",
                    p.content AS "content",
                    p.created_at AS "createdAt",
                    p.parent_id AS "parentId",
                    p.root_id AS "rootId",
                    p.reply_count AS "replyCount",
                    p.like_count AS "likeCount",
                    ROUND((
                        ts_rank_cd(p.search_vector, plainto_tsquery('simple', :query))
                        + (LEAST(p.like_count, 20) * 0.05)
                        + (LEAST(p.reply_count, 20) * 0.03)
                    )::numeric, 8) AS "searchScore"
                FROM posts p
                WHERE p.search_vector @@ plainto_tsquery('simple', :query)
            )
            SELECT
                rp."id",
                rp."authorId",
                rp."content",
                rp."createdAt",
                rp."parentId",
                rp."rootId",
                rp."replyCount",
                rp."likeCount",
                rp."searchScore"
            FROM ranked_posts rp
            WHERE (
                :cursorScore IS NULL
                OR rp."searchScore" < :cursorScore
                OR (
                    rp."searchScore" = :cursorScore
                    AND (
                        rp."createdAt" < :cursorCreatedAt
                        OR (rp."createdAt" = :cursorCreatedAt AND rp."id" < :cursorId)
                    )
                )
            )
            ORDER BY rp."searchScore" DESC, rp."createdAt" DESC, rp."id" DESC
            LIMIT :pageSize
            """, nativeQuery = true)
    List<PostSearchProjection> searchPosts(@Param("query") String query,
                                           @Param("cursorScore") BigDecimal cursorScore,
                                           @Param("cursorCreatedAt") Instant cursorCreatedAt,
                                           @Param("cursorId") UUID cursorId,
                                           @Param("pageSize") int pageSize);

    boolean existsByParentId(UUID parentId);
}
