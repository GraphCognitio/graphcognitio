package io.github.eduardopaim.graphcognitio.posts.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.eduardopaim.graphcognitio.embeddings.EmbeddingVector;
import io.github.eduardopaim.graphcognitio.entity.UserEntity;
import io.github.eduardopaim.graphcognitio.entity.UserRole;
import io.github.eduardopaim.graphcognitio.posts.dto.FeedResponse;
import io.github.eduardopaim.graphcognitio.repository.JdbcPostSearchProjection;
import io.github.eduardopaim.graphcognitio.repository.PostEmbeddingJdbcRepository;
import io.github.eduardopaim.graphcognitio.repository.PostLikeRepository;
import io.github.eduardopaim.graphcognitio.repository.PostRepository;
import io.github.eduardopaim.graphcognitio.repository.PostSearchProjection;
import io.github.eduardopaim.graphcognitio.repository.UserRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SearchServiceTest {

        @Mock
        private PostRepository postRepository;

        @Mock
        private PostEmbeddingJdbcRepository postEmbeddingJdbcRepository;

        @Mock
        private UserRepository userRepository;

        @Mock
        private PostLikeRepository postLikeRepository;

        @Mock
        private TopicDiscoveryService topicDiscoveryService;

        @Mock
        private PostEmbeddingService postEmbeddingService;

        private SearchService searchService;

        @BeforeEach
        void setUp() {
                searchService = new SearchService(
                                postRepository,
                                postEmbeddingJdbcRepository,
                                userRepository,
                                postLikeRepository,
                                topicDiscoveryService,
                                postEmbeddingService);
        }

        @Test
        void searchUsesEmbeddingCandidatesWhenEnabled() {
                UUID viewerId = UUID.randomUUID();
                UUID focusedId = UUID.randomUUID();
                UUID genericId = UUID.randomUUID();
                UUID authorA = UUID.randomUUID();
                UUID authorB = UUID.randomUUID();

                when(topicDiscoveryService.buildSemanticSearchQuery("semantic graph")).thenReturn("semantic graph");
                when(topicDiscoveryService.deriveTopicKeywords("semantic graph", 6))
                                .thenReturn(List.of("semantic", "graph"));
                when(topicDiscoveryService.deriveTopicKeywords("semantic graph ranking for conversations", 6))
                                .thenReturn(List.of("semantic", "graph", "ranking"));
                when(topicDiscoveryService.deriveTopicKeywords("generic release notes and changelog", 6))
                                .thenReturn(List.of("release", "notes"));
                when(postEmbeddingService.isEnabled()).thenReturn(true);
                when(postEmbeddingService.embedContent(any()))
                                .thenReturn(new EmbeddingVector("semantic-hash-v3", 3,
                                                new float[] { 0.1f, 0.2f, 0.3f }));
                when(postEmbeddingJdbcRepository.searchPostsByEmbedding(any(EmbeddingVector.class), anyInt()))
                                .thenReturn(List.of(
                                                projection(focusedId, authorA,
                                                                "semantic graph ranking for conversations", "0.82"),
                                                projection(genericId, authorB, "generic release notes and changelog",
                                                                "0.81")));

                when(userRepository.findAllById(Set.of(authorA, authorB))).thenReturn(List.of(
                                user(authorA, "Ada", UserRole.MOD),
                                user(authorB, "Grace", UserRole.USER)));
                when(postLikeRepository.findLikedPostIdsByUserIdAndPostIdIn(viewerId, Set.of(focusedId, genericId)))
                                .thenReturn(Set.of(focusedId));

                FeedResponse response = searchService.search("semantic graph", null, 10, viewerId);

                assertThat(response.items()).hasSize(2);
                assertThat(response.items().get(0).id()).isEqualTo(focusedId);
                assertThat(response.items().get(0).likedByMe()).isTrue();
                assertThat(response.items().get(0).authorRole()).isEqualTo(UserRole.MOD);
                verify(postEmbeddingJdbcRepository).searchPostsByEmbedding(any(EmbeddingVector.class), anyInt());
                verify(postRepository, never()).searchPosts(any(), any(), any(), any(), anyInt());
        }

        @Test
        void searchFallsBackToLexicalSearchWhenEmbeddingsAreDisabled() {
                UUID viewerId = UUID.randomUUID();
                UUID postId = UUID.randomUUID();
                UUID authorId = UUID.randomUUID();

                when(topicDiscoveryService.buildSemanticSearchQuery("frutiger bubble")).thenReturn("frutiger bubble");
                when(topicDiscoveryService.deriveTopicKeywords("glass bubble interface"))
                                .thenReturn(List.of("glass", "bubble"));
                when(postEmbeddingService.isEnabled()).thenReturn(false);
                when(postRepository.searchPosts(eq("frutiger bubble"), eq(null), eq(null), eq(null), eq(11)))
                                .thenReturn(List.of(projection(postId, authorId, "glass bubble interface", "0.74")));
                when(userRepository.findAllById(Set.of(authorId)))
                                .thenReturn(List.of(user(authorId, "Eduardo", UserRole.USER)));
                when(postLikeRepository.findLikedPostIdsByUserIdAndPostIdIn(viewerId, Set.of(postId)))
                                .thenReturn(Set.of());

                FeedResponse response = searchService.search("frutiger bubble", null, 10, viewerId);

                assertThat(response.items()).hasSize(1);
                assertThat(response.items().get(0).id()).isEqualTo(postId);
                assertThat(response.items().get(0).topicKeywords()).containsExactly("glass", "bubble");
                verify(postRepository).searchPosts(eq("frutiger bubble"), eq(null), eq(null), eq(null), eq(11));
        }

        private PostSearchProjection projection(UUID id, UUID authorId, String content, String score) {
                return new JdbcPostSearchProjection(
                                id,
                                authorId,
                                content,
                                Instant.parse("2026-02-27T18:30:00Z"),
                                null,
                                null,
                                id,
                                2,
                                4,
                                new BigDecimal(score));
        }

        private UserEntity user(UUID id, String name, UserRole role) {
                UserEntity user = new UserEntity();
                user.setId(id);
                user.setName(name);
                user.setEmail(name.toLowerCase() + "@example.com");
                user.setRole(role);
                return user;
        }
}
