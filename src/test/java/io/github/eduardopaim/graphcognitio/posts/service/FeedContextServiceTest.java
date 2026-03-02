package io.github.eduardopaim.graphcognitio.posts.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

import io.github.eduardopaim.graphcognitio.entity.UserEntity;
import io.github.eduardopaim.graphcognitio.repository.PostFeedProjection;
import io.github.eduardopaim.graphcognitio.repository.PostRepository;
import io.github.eduardopaim.graphcognitio.repository.UserRepository;
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
class FeedContextServiceTest {

    @Mock
    private PostRepository postRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private TopicDiscoveryService topicDiscoveryService;

    @InjectMocks
    private FeedContextService feedContextService;

    @Test
    void groupsPostsIntoClusters() {
        UUID postId = UUID.randomUUID();
        UUID authorId = UUID.randomUUID();
        PostFeedProjection projection = new TestPostFeedProjection(postId, authorId, "This is about graph context",
                Instant.now(), UUID.randomUUID(), UUID.randomUUID(), 2, 4, BigDecimal.valueOf(12),
                BigDecimal.valueOf(0.5));

        when(postRepository.findRelevantRootPosts(null, null, null, 15)).thenReturn(List.of(projection));
        when(userRepository.findAllById(List.of(authorId))).thenReturn(List.of(user(authorId, "Graph Master")));
        when(topicDiscoveryService.deriveTopicKeywords(projection.getContent(), 3))
                .thenReturn(List.of("graph", "context"));

        var response = feedContextService.getContextFeed(null, 3, null, null);

        assertEquals(2, response.clusters().size());
        assertEquals("graph", response.clusters().get(0).clusterId());
        assertEquals("context", response.clusters().get(1).clusterId());
    }

    private UserEntity user(UUID id, String name) {
        UserEntity user = new UserEntity();
        user.setId(id);
        user.setName(name);
        user.setEmail(name.toLowerCase().replace(" ", "@example.com"));
        user.setPasswordHash("hash");
        user.setRole(io.github.eduardopaim.graphcognitio.entity.UserRole.USER);
        user.setCreatedAt(Instant.now());
        return user;
    }

    private static final class TestPostFeedProjection implements PostFeedProjection {
        private final UUID id;
        private final UUID authorId;
        private final String content;
        private final Instant createdAt;
        private final UUID parentId;
        private final UUID rootId;
        private final int replyCount;
        private final int likeCount;
        private final BigDecimal feedScore;
        private final BigDecimal semanticDensity;

        private TestPostFeedProjection(UUID id, UUID authorId, String content, Instant createdAt, UUID parentId,
                UUID rootId, int replyCount, int likeCount, BigDecimal feedScore, BigDecimal semanticDensity) {
            this.id = id;
            this.authorId = authorId;
            this.content = content;
            this.createdAt = createdAt;
            this.parentId = parentId;
            this.rootId = rootId;
            this.replyCount = replyCount;
            this.likeCount = likeCount;
            this.feedScore = feedScore;
            this.semanticDensity = semanticDensity;
        }

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
        public BigDecimal getFeedScore() {
            return feedScore;
        }

        @Override
        public BigDecimal getSemanticDensity() {
            return semanticDensity;
        }
    }
}
