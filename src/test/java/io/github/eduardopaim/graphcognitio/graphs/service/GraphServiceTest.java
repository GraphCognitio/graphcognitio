package io.github.eduardopaim.graphcognitio.graphs.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.eduardopaim.graphcognitio.config.FeatureFlagsProperties;
import io.github.eduardopaim.graphcognitio.entity.EdgeEntity;
import io.github.eduardopaim.graphcognitio.entity.EdgeType;
import io.github.eduardopaim.graphcognitio.entity.PostEntity;
import io.github.eduardopaim.graphcognitio.entity.UserEntity;
import io.github.eduardopaim.graphcognitio.exception.ResourceNotFoundException;
import io.github.eduardopaim.graphcognitio.graphs.GraphMode;
import io.github.eduardopaim.graphcognitio.graphs.dto.ConversationGraphResponse;
import io.github.eduardopaim.graphcognitio.posts.service.TopicMatchResult;
import io.github.eduardopaim.graphcognitio.posts.service.TopicDiscoveryService;
import io.github.eduardopaim.graphcognitio.repository.EdgeRepository;
import io.github.eduardopaim.graphcognitio.repository.PostRepository;
import io.github.eduardopaim.graphcognitio.repository.PostSearchProjection;
import io.github.eduardopaim.graphcognitio.repository.UserRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GraphServiceTest {

    @Mock
    private PostRepository postRepository;

    @Mock
    private EdgeRepository edgeRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private FeatureFlagsProperties featureFlagsProperties;

    @Mock
    private TopicDiscoveryService topicDiscoveryService;

    @InjectMocks
    private GraphService graphService;

    @Test
    void getConversationGraphReturnsOnlyRootWhenDepthIsZero() {
        UUID rootId = UUID.fromString("2fbf8f22-5485-4f95-a9c0-1723a49bfd7c");
        UUID authorId = UUID.fromString("d6a63297-3f0f-4da4-9014-097bb6ebcb55");
        PostEntity root = post(rootId, authorId, "root", Instant.parse("2026-02-26T21:10:00Z"), null, rootId, 0);

        when(postRepository.findByIdAndParentIdIsNull(rootId)).thenReturn(Optional.of(root));
        when(userRepository.findAllById(any())).thenReturn(List.of(author(authorId, "Eduardo")));

        ConversationGraphResponse response = graphService.getConversationGraph(rootId, 0, 120, GraphMode.CONVERSATION);

        assertEquals(1, response.nodes().size());
        assertEquals(rootId, response.nodes().get(0).id());
        assertEquals(0, response.edges().size());
        verify(postRepository, never()).findByParentIdInOrderByCreatedAtAscIdAsc(any());
        verify(edgeRepository, never()).findBySourcePostIdInOrderByCreatedAtAscIdAsc(any());
    }

    @Test
    void getConversationGraphAppliesBfsAndLimitIncludingRoot() {
        UUID rootId = UUID.fromString("2fbf8f22-5485-4f95-a9c0-1723a49bfd7c");
        UUID childOneId = UUID.fromString("bbdd2f10-c028-4058-b7f6-f0e74f0ed78d");
        UUID childTwoId = UUID.fromString("dc4a2480-0619-4512-8733-fb30bcf8dca8");
        UUID authorId = UUID.fromString("d6a63297-3f0f-4da4-9014-097bb6ebcb55");

        PostEntity root = post(rootId, authorId, "root", Instant.parse("2026-02-26T21:10:00Z"), null, rootId, 2);
        PostEntity childOne = post(
                childOneId,
                authorId,
                "A".repeat(200),
                Instant.parse("2026-02-26T21:11:00Z"),
                rootId,
                rootId,
                0);
        PostEntity childTwo = post(
                childTwoId,
                authorId,
                "child-2",
                Instant.parse("2026-02-26T21:12:00Z"),
                rootId,
                rootId,
                0);
        EdgeEntity edgeOne = edge(
                UUID.fromString("c84ebcc0-f921-47f9-9736-fee56ec30225"),
                childOneId,
                rootId,
                Instant.parse("2026-02-26T21:11:00Z"));
        EdgeEntity edgeTwo = edge(
                UUID.fromString("d43cc4e3-a53f-4e4b-af26-65cc293af679"),
                childTwoId,
                rootId,
                Instant.parse("2026-02-26T21:12:00Z"));

        when(postRepository.findByIdAndParentIdIsNull(rootId)).thenReturn(Optional.of(root));
        when(postRepository.findByParentIdInOrderByCreatedAtAscIdAsc(eq(List.of(rootId))))
                .thenReturn(List.of(childOne, childTwo));
        when(edgeRepository.findBySourcePostIdInOrderByCreatedAtAscIdAsc(eq(List.of(childOneId, childTwoId))))
                .thenReturn(List.of(edgeOne, edgeTwo));
        when(userRepository.findAllById(any())).thenReturn(List.of(author(authorId, "Eduardo")));

        ConversationGraphResponse response = graphService.getConversationGraph(rootId, 2, 3, GraphMode.CONVERSATION);

        assertEquals(3, response.nodes().size());
        assertEquals(rootId, response.nodes().get(0).id());
        assertEquals(childOneId, response.nodes().get(1).id());
        assertEquals(childTwoId, response.nodes().get(2).id());
        assertEquals(2, response.edges().size());
        assertEquals(edgeOne.getId(), response.edges().get(0).id());
        assertEquals(edgeTwo.getId(), response.edges().get(1).id());
        assertEquals(140, response.nodes().get(1).contentPreview().length());
        verify(postRepository, never()).findByParentIdInOrderByCreatedAtAscIdAsc(eq(List.of(childOneId, childTwoId)));
    }

    @Test
    void getConversationGraphThrowsWhenRootNotFound() {
        UUID rootId = UUID.fromString("2fbf8f22-5485-4f95-a9c0-1723a49bfd7c");
        when(postRepository.findByIdAndParentIdIsNull(rootId)).thenReturn(Optional.empty());

        ResourceNotFoundException exception = assertThrows(
                ResourceNotFoundException.class,
                () -> graphService.getConversationGraph(rootId, 2, 120, GraphMode.CONVERSATION));

        assertEquals("Root post not found", exception.getMessage());
    }

    @Test
    void getConversationGraphReturnsTopicMatchesInTopicMode() {
        UUID rootId = UUID.fromString("2fbf8f22-5485-4f95-a9c0-1723a49bfd7c");
        UUID relatedId = UUID.fromString("bbdd2f10-c028-4058-b7f6-f0e74f0ed78d");
        UUID authorId = UUID.fromString("d6a63297-3f0f-4da4-9014-097bb6ebcb55");

        PostEntity root = post(rootId, authorId, "graph context root", Instant.parse("2026-02-26T21:10:00Z"), null,
                rootId, 2);

        when(postRepository.findByIdAndParentIdIsNull(rootId)).thenReturn(Optional.of(root));
        when(topicDiscoveryService.findRelatedRootPosts(root, 8))
                .thenReturn(List.of(new TopicMatchResult(
                        projection(
                                relatedId,
                                authorId,
                                "related topic root",
                                Instant.parse("2026-02-26T21:12:00Z"),
                                null,
                                relatedId,
                                0,
                                4),
                        0.874,
                        List.of("graph", "context"),
                        "Embedding match around: graph, context")));
        when(userRepository.findAllById(any())).thenReturn(List.of(author(authorId, "Eduardo")));

        ConversationGraphResponse response = graphService.getConversationGraph(rootId, 2, 12, GraphMode.TOPIC);

        assertEquals(2, response.nodes().size());
        assertEquals(1, response.edges().size());
        assertEquals("RELATED", response.edges().get(0).type());
        assertEquals(rootId, response.edges().get(0).sourcePostId());
        assertEquals(relatedId, response.edges().get(0).targetPostId());
        assertEquals("Embedding match around: graph, context", response.nodes().get(1).topicReason());
        verify(postRepository, never()).findByParentIdInOrderByCreatedAtAscIdAsc(any());
        verify(edgeRepository, never()).findBySourcePostIdInOrderByCreatedAtAscIdAsc(any());
    }

    @Test
    void getConversationGraphAppendsTopicMatchesInHybridMode() {
        UUID rootId = UUID.fromString("2fbf8f22-5485-4f95-a9c0-1723a49bfd7c");
        UUID childId = UUID.fromString("bbdd2f10-c028-4058-b7f6-f0e74f0ed78d");
        UUID relatedId = UUID.fromString("dc4a2480-0619-4512-8733-fb30bcf8dca8");
        UUID authorId = UUID.fromString("d6a63297-3f0f-4da4-9014-097bb6ebcb55");

        PostEntity root = post(rootId, authorId, "hybrid root", Instant.parse("2026-02-26T21:10:00Z"), null, rootId, 1);
        PostEntity child = post(childId, authorId, "child", Instant.parse("2026-02-26T21:11:00Z"), rootId, rootId, 0);
        EdgeEntity replyEdge = edge(
                UUID.fromString("c84ebcc0-f921-47f9-9736-fee56ec30225"),
                childId,
                rootId,
                Instant.parse("2026-02-26T21:11:00Z"));

        when(postRepository.findByIdAndParentIdIsNull(rootId)).thenReturn(Optional.of(root));
        when(postRepository.findByParentIdInOrderByCreatedAtAscIdAsc(eq(List.of(rootId))))
                .thenReturn(List.of(child));
        when(edgeRepository.findBySourcePostIdInOrderByCreatedAtAscIdAsc(eq(List.of(childId))))
                .thenReturn(List.of(replyEdge));
        when(topicDiscoveryService.findRelatedRootPosts(root, 8))
                .thenReturn(List.of(new TopicMatchResult(
                        projection(
                                relatedId,
                                authorId,
                                "neighboring conversation",
                                Instant.parse("2026-02-26T21:12:00Z"),
                                null,
                                relatedId,
                                2,
                                5),
                        0.661,
                        List.of("hybrid", "root"),
                        "Embedding match around: hybrid, root")));
        when(userRepository.findAllById(any())).thenReturn(List.of(author(authorId, "Eduardo")));

        ConversationGraphResponse response = graphService.getConversationGraph(rootId, 1, 10, GraphMode.HYBRID);

        assertEquals(3, response.nodes().size());
        assertEquals(2, response.edges().size());
        assertEquals("REPLY", response.edges().get(0).type());
        assertEquals("RELATED", response.edges().get(1).type());
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

    private EdgeEntity edge(UUID id, UUID sourceId, UUID targetId, Instant createdAt) {
        EdgeEntity edge = new EdgeEntity();
        edge.setId(id);
        edge.setSourcePostId(sourceId);
        edge.setTargetPostId(targetId);
        edge.setType(EdgeType.REPLY);
        edge.setCreatedAt(createdAt);
        return edge;
    }

    private UserEntity author(UUID id, String name) {
        UserEntity user = new UserEntity();
        user.setId(id);
        user.setName(name);
        user.setEmail(name.toLowerCase() + "@example.com");
        user.setPasswordHash("hash");
        user.setCreatedAt(Instant.parse("2026-02-26T21:10:00Z"));
        return user;
    }

    private PostSearchProjection projection(UUID id,
            UUID authorId,
            String content,
            Instant createdAt,
            UUID parentId,
            UUID rootId,
            int replyCount,
            int likeCount) {
        return new PostSearchProjection() {
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
                return parentId;
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
            public java.math.BigDecimal getSearchScore() {
                return java.math.BigDecimal.ONE;
            }
        };
    }
}
