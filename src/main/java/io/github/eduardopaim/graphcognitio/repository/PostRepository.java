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

    List<PostEntity> findByRootIdOrderByCreatedAtAscIdAsc(UUID rootId);

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

    @Query("""
            SELECT p
            FROM PostEntity p
            WHERE p.authorId = (SELECT u.id FROM UserEntity u WHERE u.username = :username)
              AND p.parentId IS NULL
            ORDER BY p.createdAt DESC, p.id DESC
            """)
    List<PostEntity> findRootPostsByUsernameWithoutCursor(@Param("username") String username, Pageable pageable);

    @Query(value = """
            SELECT p.*
            FROM posts p
            JOIN users u ON p.author_id = u.id
            WHERE u.username = :username
              AND p.parent_id IS NULL
              AND (
                    p.created_at < :cursorCreatedAt
                    OR (p.created_at = :cursorCreatedAt AND p.id < :cursorId)
              )
            ORDER BY p.created_at DESC, p.id DESC
            """, nativeQuery = true)
    List<PostEntity> findRootPostsByUsernameAfterCursor(@Param("username") String username,
            @Param("cursorCreatedAt") Instant cursorCreatedAt,
            @Param("cursorId") UUID cursorId,
            Pageable pageable);

    @Query(value = """
            WITH thread_stats AS (
                SELECT
                    root_id AS "rootId",
                    COUNT(*) AS "threadPostCount",
                    COUNT(DISTINCT author_id) AS "participantCount"
                FROM posts
                GROUP BY root_id
            ),
            ranked_posts AS (
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

    @Query(value = """
            WITH ranked_posts AS (
                WITH semantic_context AS (
                    SELECT AVG(root.embedding) AS "semanticCentroid"
                    FROM posts root
                    WHERE root.parent_id IS NULL
                      AND root.embedding IS NOT NULL
                      AND root.created_at >= NOW() - INTERVAL '30 days'
                )
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
                WHERE p.parent_id IS NULL
                  AND p.id <> :excludedPostId
                  AND p.root_id <> :excludedRootId
                  AND p.search_vector @@ plainto_tsquery('simple', :query)
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
            ORDER BY rp."searchScore" DESC, rp."createdAt" DESC, rp."id" DESC
            LIMIT :pageSize
            """, nativeQuery = true)
    List<PostSearchProjection> findRelatedRootPosts(@Param("excludedPostId") UUID excludedPostId,
            @Param("excludedRootId") UUID excludedRootId,
            @Param("query") String query,
            @Param("pageSize") int pageSize);

    @Query(value = """
            WITH thread_stats AS (
                SELECT
                    root_id AS "rootId",
                    COUNT(*) AS "threadPostCount",
                    COUNT(DISTINCT author_id) AS "participantCount"
                FROM posts
                GROUP BY root_id
            ),
            ranked_posts AS (
                SELECT
                    p.id AS "id",
                    p.author_id AS "authorId",
                    p.content AS "content",
                    p.created_at AS "createdAt",
                    p.parent_id AS "parentId",
                    p.root_id AS "rootId",
                    p.reply_count AS "replyCount",
                    p.like_count AS "likeCount",
                    ROUND(COALESCE(sn."nearestSimilarity", 0)::numeric, 8) AS "semanticDensity",
                    ROUND((
                        (LEAST(p.like_count, 20) * 0.06)
                        + (LEAST(p.reply_count, 20) * 0.045)
                        + CASE
                            WHEN p.created_at >= NOW() - INTERVAL '6 hours' THEN 1.35
                            WHEN p.created_at >= NOW() - INTERVAL '24 hours' THEN 0.95
                            WHEN p.created_at >= NOW() - INTERVAL '3 days' THEN 0.45
                            WHEN p.created_at >= NOW() - INTERVAL '7 days' THEN 0.12
                            ELSE 0
                        END
                        + (LEAST(GREATEST(COALESCE(ts."participantCount", 1) - 1, 0), 6) * 0.08)
                        - (GREATEST(COALESCE(ts."threadPostCount", 1) - 18, 0) * 0.01)
                        + (COALESCE(sn."nearestSimilarity", 0) * 0.45)
                    )::numeric, 8) AS "feedScore"
                FROM posts p
                LEFT JOIN thread_stats ts ON ts."rootId" = p.id
                LEFT JOIN LATERAL (
                    SELECT GREATEST(1 - (p.embedding <=> peer.embedding), 0) AS "nearestSimilarity"
                    FROM posts peer
                    WHERE p.embedding IS NOT NULL
                      AND peer.parent_id IS NULL
                      AND peer.id <> p.id
                      AND peer.embedding IS NOT NULL
                      AND peer.created_at >= NOW() - INTERVAL '30 days'
                    ORDER BY peer.embedding <=> p.embedding
                    LIMIT 1
                ) sn ON true
                WHERE p.parent_id IS NULL
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
                rp."feedScore",
                rp."semanticDensity"
            FROM ranked_posts rp
            WHERE (
                :cursorScore IS NULL
                OR rp."feedScore" < :cursorScore
                OR (
                    rp."feedScore" = :cursorScore
                    AND (
                        rp."createdAt" < :cursorCreatedAt
                        OR (rp."createdAt" = :cursorCreatedAt AND rp."id" < :cursorId)
                    )
                )
            )
            ORDER BY rp."feedScore" DESC, rp."createdAt" DESC, rp."id" DESC
            LIMIT :pageSize
            """, nativeQuery = true)
    List<PostFeedProjection> findRelevantRootPosts(@Param("cursorScore") BigDecimal cursorScore,
            @Param("cursorCreatedAt") Instant cursorCreatedAt,
            @Param("cursorId") UUID cursorId,
            @Param("pageSize") int pageSize);

    boolean existsByParentId(UUID parentId);
}
