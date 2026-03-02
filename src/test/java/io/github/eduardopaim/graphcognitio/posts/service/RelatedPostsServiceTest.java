package io.github.eduardopaim.graphcognitio.posts.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import io.github.eduardopaim.graphcognitio.entity.PostEntity;
import io.github.eduardopaim.graphcognitio.entity.UserEntity;
import io.github.eduardopaim.graphcognitio.entity.UserRole;
import io.github.eduardopaim.graphcognitio.posts.dto.RelatedPostResponse;
import io.github.eduardopaim.graphcognitio.repository.PostLikeRepository;
import io.github.eduardopaim.graphcognitio.repository.PostRepository;
import io.github.eduardopaim.graphcognitio.repository.PostSearchProjection;
import io.github.eduardopaim.graphcognitio.repository.UserRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RelatedPostsServiceTest {

    @Mock
    private PostRepository postRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private PostLikeRepository postLikeRepository;

    @Mock
    private TopicDiscoveryService topicDiscoveryService;

    @InjectMocks
    private RelatedPostsService relatedPostsService;

    @Test
    void getRelatedPostsReturnsRankedOtherRootPosts() {
        UUID viewerId = UUID.fromString("d6a63297-3f0f-4da4-9014-097bb6ebcb55");
        UUID seedId = UUID.fromString("2fbf8f22-5485-4f95-a9c0-1723a49bfd7c");
        UUID seedRootId = UUID.fromString("bbdd2f10-c028-4058-b7f6-f0e74f0ed78d");
        UUID relatedId = UUID.fromString("dc4a2480-0619-4512-8733-fb30bcf8dca8");
        UUID authorId = UUID.fromString("338ed4fe-f51d-423d-8fd3-c35c4f740fc7");

        PostEntity seed = new PostEntity();
        seed.setId(seedId);
        seed.setAuthorId(authorId);
        seed.setContent("glass graph signal");
        seed.setCreatedAt(Instant.parse("2026-02-26T21:00:00Z"));
        seed.setParentId(UUID.fromString("e76c72b6-c874-4b0a-bf85-6ac5b74ff7b6"));
        seed.setRootId(seedRootId);

        when(postRepository.findById(seedId)).thenReturn(Optional.of(seed));
        when(topicDiscoveryService.findRelatedRootPosts(seed, 6))
                .thenReturn(List.of(new TopicMatchResult(
                        searchProjection(relatedId, authorId, "glass graph root", Instant.parse("2026-02-26T22:00:00Z"),
                                relatedId, 4, 5),
                        1.184,
                        List.of("glass", "graph"),
                        "Embedding match around: glass, graph")));
        when(userRepository.findAllById(any())).thenReturn(List.of(author(authorId, "Eduardo")));
        when(postLikeRepository.findLikedPostIdsByUserIdAndPostIdIn(eq(viewerId), any())).thenReturn(Set.of(relatedId));

        List<RelatedPostResponse> response = relatedPostsService.getRelatedPosts(seedId, viewerId, 6);

        assertEquals(1, response.size());
        assertEquals(relatedId, response.get(0).id());
        assertEquals(relatedId, response.get(0).rootId());
        assertTrue(response.get(0).likedByMe());
        assertEquals("Embedding match around: glass, graph", response.get(0).topicReason());
    }

    private PostSearchProjection searchProjection(UUID id,
            UUID authorId,
            String content,
            Instant createdAt,
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
                return new BigDecimal("0.52");
            }
        };
    }

    private UserEntity author(UUID id, String name) {
        UserEntity user = new UserEntity();
        user.setId(id);
        user.setName(name);
        user.setEmail(name.toLowerCase() + "@example.com");
        user.setPasswordHash("hash");
        user.setRole(UserRole.USER);
        user.setCreatedAt(Instant.parse("2026-02-26T21:10:00Z"));
        return user;
    }
}
