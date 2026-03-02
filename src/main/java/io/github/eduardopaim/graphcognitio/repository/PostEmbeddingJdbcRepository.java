package io.github.eduardopaim.graphcognitio.repository;

import io.github.eduardopaim.graphcognitio.embeddings.EmbeddingVector;
import io.github.eduardopaim.graphcognitio.entity.PostEntity;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class PostEmbeddingJdbcRepository {

    private final JdbcTemplate jdbcTemplate;

    public PostEmbeddingJdbcRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void updateEmbedding(UUID postId, EmbeddingVector embedding, Instant updatedAt) {
        jdbcTemplate.update(
                """
                        UPDATE posts
                        SET embedding = CAST(? AS vector),
                            embedding_model = ?,
                            embedding_updated_at = ?
                        WHERE id = ?
                        """,
                embedding.toPgVectorLiteral(),
                embedding.model(),
                Timestamp.from(updatedAt),
                postId);
    }

    public List<PostEntity> findPostsForReindex(String targetModel, int limit, boolean missingOnly) {
        String sql = missingOnly
                ? """
                        SELECT id, author_id, content, created_at, parent_id, root_id, reply_count, like_count
                        FROM posts
                        WHERE embedding IS NULL
                           OR embedding_model IS NULL
                           OR embedding_model <> ?
                        ORDER BY created_at DESC, id DESC
                        LIMIT ?
                        """
                : """
                        SELECT id, author_id, content, created_at, parent_id, root_id, reply_count, like_count
                        FROM posts
                        ORDER BY created_at DESC, id DESC
                        LIMIT ?
                        """;

        Object[] args = missingOnly ? new Object[] { targetModel, limit } : new Object[] { limit };
        return jdbcTemplate.query(sql, args, (resultSet, rowNum) -> {
            PostEntity post = new PostEntity();
            post.setId(resultSet.getObject("id", UUID.class));
            post.setAuthorId(resultSet.getObject("author_id", UUID.class));
            post.setContent(resultSet.getString("content"));
            post.setCreatedAt(resultSet.getTimestamp("created_at").toInstant());
            post.setParentId(resultSet.getObject("parent_id", UUID.class));
            post.setRootId(resultSet.getObject("root_id", UUID.class));
            post.setReplyCount(resultSet.getInt("reply_count"));
            post.setLikeCount(resultSet.getInt("like_count"));
            return post;
        });
    }

    public List<PostSearchProjection> findRelatedRootPostsByEmbedding(UUID excludedPostId,
            UUID excludedRootId,
            EmbeddingVector embedding,
            int pageSize) {
        return jdbcTemplate.query(
                """
                        SELECT
                            p.id,
                            p.author_id,
                            p.content,
                            p.created_at,
                            p.updated_at,
                            p.parent_id,
                            p.root_id,
                            p.reply_count,
                            p.like_count,
                            ROUND((1 - (p.embedding <=> CAST(? AS vector)))::numeric, 8) AS similarity
                        FROM posts p
                        WHERE p.parent_id IS NULL
                          AND p.id <> ?
                          AND p.root_id <> ?
                          AND p.embedding IS NOT NULL
                        ORDER BY p.embedding <=> CAST(? AS vector), p.created_at DESC, p.id DESC
                        LIMIT ?
                        """,
                new Object[] {
                        embedding.toPgVectorLiteral(),
                        excludedPostId,
                        excludedRootId,
                        embedding.toPgVectorLiteral(),
                        pageSize
                },
                (resultSet, rowNum) -> new JdbcPostSearchProjection(
                        resultSet.getObject("id", UUID.class),
                        resultSet.getObject("author_id", UUID.class),
                        resultSet.getString("content"),
                        resultSet.getTimestamp("created_at").toInstant(),
                        resultSet.getTimestamp("updated_at") != null ? resultSet.getTimestamp("updated_at").toInstant()
                                : null,
                        resultSet.getObject("parent_id", UUID.class),
                        resultSet.getObject("root_id", UUID.class),
                        resultSet.getInt("reply_count"),
                        resultSet.getInt("like_count"),
                        resultSet.getBigDecimal("similarity") == null ? BigDecimal.ZERO
                                : resultSet.getBigDecimal("similarity")));
    }

    public List<PostSearchProjection> searchPostsByEmbedding(EmbeddingVector embedding, int pageSize) {
        return jdbcTemplate.query(
                """
                        SELECT
                            p.id,
                            p.author_id,
                            p.content,
                            p.created_at,
                            p.updated_at,
                            p.parent_id,
                            p.root_id,
                            p.reply_count,
                            p.like_count,
                            ROUND((1 - (p.embedding <=> CAST(? AS vector)))::numeric, 8) AS similarity
                        FROM posts p
                        WHERE p.embedding IS NOT NULL
                        ORDER BY p.embedding <=> CAST(? AS vector), p.created_at DESC, p.id DESC
                        LIMIT ?
                        """,
                new Object[] {
                        embedding.toPgVectorLiteral(),
                        embedding.toPgVectorLiteral(),
                        pageSize
                },
                (resultSet, rowNum) -> new JdbcPostSearchProjection(
                        resultSet.getObject("id", UUID.class),
                        resultSet.getObject("author_id", UUID.class),
                        resultSet.getString("content"),
                        resultSet.getTimestamp("created_at").toInstant(),
                        resultSet.getTimestamp("updated_at") != null ? resultSet.getTimestamp("updated_at").toInstant()
                                : null,
                        resultSet.getObject("parent_id", UUID.class),
                        resultSet.getObject("root_id", UUID.class),
                        resultSet.getInt("reply_count"),
                        resultSet.getInt("like_count"),
                        resultSet.getBigDecimal("similarity") == null ? BigDecimal.ZERO
                                : resultSet.getBigDecimal("similarity")));
    }
}
