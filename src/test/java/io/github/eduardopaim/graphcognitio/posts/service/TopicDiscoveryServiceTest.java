package io.github.eduardopaim.graphcognitio.posts.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import io.github.eduardopaim.graphcognitio.embeddings.EmbeddingVector;
import io.github.eduardopaim.graphcognitio.entity.PostEntity;
import io.github.eduardopaim.graphcognitio.repository.PostEmbeddingJdbcRepository;
import io.github.eduardopaim.graphcognitio.repository.PostRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TopicDiscoveryServiceTest {

    @Mock
    private PostRepository postRepository;

    @Mock
    private PostEmbeddingJdbcRepository postEmbeddingJdbcRepository;

    @Mock
    private PostEmbeddingService postEmbeddingService;

    @InjectMocks
    private TopicDiscoveryService topicDiscoveryService;

    @Test
    void findRelatedRootPostsReranksCandidatesByContextOverlap() {
        UUID rootId = UUID.fromString("2fbf8f22-5485-4f95-a9c0-1723a49bfd7c");
        UUID seedId = UUID.fromString("bbdd2f10-c028-4058-b7f6-f0e74f0ed78d");
        UUID authorId = UUID.fromString("d6a63297-3f0f-4da4-9014-097bb6ebcb55");
        UUID topicalId = UUID.fromString("dc4a2480-0619-4512-8733-fb30bcf8dca8");
        UUID genericId = UUID.fromString("c84ebcc0-f921-47f9-9736-fee56ec30225");

        PostEntity root = post(rootId, authorId, "graph cognition mapping", Instant.parse("2026-02-26T21:10:00Z"), null,
                rootId, 2);
        PostEntity seed = post(seedId, authorId, "semantic graph clusters for context",
                Instant.parse("2026-02-26T21:12:00Z"), rootId, rootId, 0);

        when(postEmbeddingService.isEnabled()).thenReturn(true);
        when(postEmbeddingService.embedContent(anyString()))
                .thenReturn(new EmbeddingVector("semantic-hash-v3", 3, new float[] { 0.1f, 0.2f, 0.3f }));
        when(postRepository.findByRootIdOrderByCreatedAtAscIdAsc(rootId)).thenReturn(List.of(root, seed));
        when(postEmbeddingJdbcRepository.findRelatedRootPostsByEmbedding(eq(seedId), eq(rootId),
                any(EmbeddingVector.class), eq(8)))
                .thenReturn(List.of(
                        projection(genericId, authorId, "general release update for platform",
                                Instant.parse("2026-02-27T09:10:00Z"), genericId, 6, 7, "0.36"),
                        projection(topicalId, authorId, "graph clusters and semantic mapping",
                                Instant.parse("2026-02-26T22:00:00Z"), topicalId, 2, 3, "0.81")));

        List<TopicMatchResult> result = topicDiscoveryService.findRelatedRootPosts(seed, 2);

        assertEquals(2, result.size());
        assertEquals(topicalId, result.get(0).projection().getId());
        assertEquals(genericId, result.get(1).projection().getId());
        assertEquals(4, result.get(0).matchedTerms().size());
        assertEquals("graph", result.get(0).matchedTerms().get(0));
    }

    private PostEntity post(UUID id,
            UUID authorId,
            String content,
            Instant createdAt,
            UUID parentId,
            UUID rootId,
            int replyCount) {
        PostEntity post = new PostEntity();
        post.setId(id);
        post.setAuthorId(authorId);
        post.setContent(content);
        post.setCreatedAt(createdAt);
        post.setParentId(parentId);
        post.setRootId(rootId);
        post.setReplyCount(replyCount);
        return post;
    }

    private io.github.eduardopaim.graphcognitio.repository.PostSearchProjection projection(UUID id,
            UUID authorId,
            String content,
            Instant createdAt,
            UUID rootId,
            int replyCount,
            int likeCount,
            String searchScore) {
        return new io.github.eduardopaim.graphcognitio.repository.PostSearchProjection() {
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
                return null;
            }

            @Override
            public UUID getParentId() {
                return null;
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
                return new BigDecimal(searchScore);
            }
        };
    }
}
