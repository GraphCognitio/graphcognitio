package io.github.eduardopaim.graphcognitio.posts.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.eduardopaim.graphcognitio.audit.service.AuditLogService;
import io.github.eduardopaim.graphcognitio.entity.EdgeEntity;
import io.github.eduardopaim.graphcognitio.entity.EdgeType;
import io.github.eduardopaim.graphcognitio.entity.PostEntity;
import io.github.eduardopaim.graphcognitio.entity.PostLikeEntity;
import io.github.eduardopaim.graphcognitio.entity.UserEntity;
import io.github.eduardopaim.graphcognitio.entity.UserRole;
import io.github.eduardopaim.graphcognitio.exception.BadRequestException;
import io.github.eduardopaim.graphcognitio.exception.ConflictException;
import io.github.eduardopaim.graphcognitio.exception.ForbiddenException;
import io.github.eduardopaim.graphcognitio.exception.ResourceNotFoundException;
import io.github.eduardopaim.graphcognitio.posts.dto.CreatePostRequest;
import io.github.eduardopaim.graphcognitio.posts.dto.FeedResponse;
import io.github.eduardopaim.graphcognitio.posts.dto.FeedSort;
import io.github.eduardopaim.graphcognitio.posts.dto.PostResponse;
import io.github.eduardopaim.graphcognitio.posts.dto.UpdatePostRequest;
import io.github.eduardopaim.graphcognitio.posts.mapper.PostMapper;
import io.github.eduardopaim.graphcognitio.repository.EdgeRepository;
import io.github.eduardopaim.graphcognitio.repository.PostFeedProjection;
import io.github.eduardopaim.graphcognitio.repository.PostLikeRepository;
import io.github.eduardopaim.graphcognitio.repository.PostRepository;
import io.github.eduardopaim.graphcognitio.repository.UserRepository;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.math.BigDecimal;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
class PostServiceTest {

    @Mock
    private PostRepository postRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private EdgeRepository edgeRepository;

    @Mock
    private PostLikeRepository postLikeRepository;

    @Mock
    private PostMapper postMapper;

    @Mock
    private AuditLogService auditLogService;

    @Mock
    private TopicDiscoveryService topicDiscoveryService;

    @Mock
    private PostEmbeddingService postEmbeddingService;

    @InjectMocks
    private PostService postService;

    @Test
    void createReplyInheritsRootAndCreatesEdgeAndIncrementsParentCount() {
        UUID authorId = UUID.fromString("d6a63297-3f0f-4da4-9014-097bb6ebcb55");
        UUID parentId = UUID.fromString("2fbf8f22-5485-4f95-a9c0-1723a49bfd7c");
        UUID rootId = UUID.fromString("dc4a2480-0619-4512-8733-fb30bcf8dca8");

        UserEntity author = new UserEntity();
        author.setId(authorId);
        author.setName("Eduardo");
        author.setRole(UserRole.USER);

        PostEntity parent = new PostEntity();
        parent.setId(parentId);
        parent.setAuthorId(UUID.fromString("338ed4fe-f51d-423d-8fd3-c35c4f740fc7"));
        parent.setContent("parent");
        parent.setCreatedAt(Instant.parse("2026-02-26T21:00:00Z"));
        parent.setParentId(UUID.fromString("ef9fca91-bf57-4fb9-a8ca-dbf8d017dd76"));
        parent.setRootId(rootId);
        parent.setReplyCount(2);

        when(userRepository.findById(authorId)).thenReturn(Optional.of(author));
        when(postRepository.findById(parentId)).thenReturn(Optional.of(parent));
        when(postRepository.save(any(PostEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(edgeRepository.save(any(EdgeEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(topicDiscoveryService.deriveTopicKeywords(any())).thenReturn(List.of("graph", "conversation"));
        when(postMapper.toResponse(any(PostEntity.class), eq("Eduardo"), isNull(), eq(UserRole.USER),
                anyBoolean(), anyList()))
                .thenAnswer(invocation -> {
                    PostEntity post = invocation.getArgument(0);
                    boolean likedByMe = invocation.getArgument(4);
                    return new PostResponse(
                            post.getId(),
                            post.getAuthorId(),
                            "Eduardo",
                            "",
                            UserRole.USER,
                            post.getContent(),
                            post.getCreatedAt(),
                            null,
                            post.getParentId(),
                            post.getRootId(),
                            post.getReplyCount(),
                            post.getLikeCount(),
                            likedByMe);
                });

        PostResponse response = postService.createReply(authorId, parentId, new CreatePostRequest("reply body"));

        ArgumentCaptor<PostEntity> postCaptor = ArgumentCaptor.forClass(PostEntity.class);
        verify(postRepository, times(2)).save(postCaptor.capture());
        List<PostEntity> savedPosts = postCaptor.getAllValues();

        PostEntity savedReply = savedPosts.stream()
                .filter(saved -> parentId.equals(saved.getParentId()))
                .findFirst()
                .orElseThrow();

        assertEquals(rootId, savedReply.getRootId());
        assertEquals("reply body", savedReply.getContent());
        assertEquals(0, savedReply.getReplyCount());
        assertEquals(3, parent.getReplyCount());

        ArgumentCaptor<EdgeEntity> edgeCaptor = ArgumentCaptor.forClass(EdgeEntity.class);
        verify(edgeRepository).save(edgeCaptor.capture());
        EdgeEntity savedEdge = edgeCaptor.getValue();
        assertEquals(savedReply.getId(), savedEdge.getSourcePostId());
        assertEquals(parentId, savedEdge.getTargetPostId());
        assertEquals(EdgeType.REPLY, savedEdge.getType());

        assertEquals(savedReply.getId(), response.id());
        assertEquals(parentId, response.parentId());
        assertEquals(rootId, response.rootId());
    }

    @Test
    void createReplyUsesParentIdAsRootWhenParentIsRoot() {
        UUID authorId = UUID.fromString("d6a63297-3f0f-4da4-9014-097bb6ebcb55");
        UUID parentId = UUID.fromString("2fbf8f22-5485-4f95-a9c0-1723a49bfd7c");

        UserEntity author = new UserEntity();
        author.setId(authorId);
        author.setName("Eduardo");
        author.setRole(UserRole.USER);

        PostEntity parent = new PostEntity();
        parent.setId(parentId);
        parent.setParentId(null);
        parent.setRootId(parentId);
        parent.setReplyCount(0);

        when(userRepository.findById(authorId)).thenReturn(Optional.of(author));
        when(postRepository.findById(parentId)).thenReturn(Optional.of(parent));
        when(postRepository.save(any(PostEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(edgeRepository.save(any(EdgeEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(topicDiscoveryService.deriveTopicKeywords(any())).thenReturn(List.of("graph", "conversation"));
        when(postMapper.toResponse(any(PostEntity.class), eq("Eduardo"), isNull(), eq(UserRole.USER),
                anyBoolean(), anyList()))
                .thenAnswer(invocation -> {
                    PostEntity post = invocation.getArgument(0);
                    boolean likedByMe = invocation.getArgument(4);
                    return new PostResponse(
                            post.getId(),
                            post.getAuthorId(),
                            "Eduardo",
                            "",
                            UserRole.USER,
                            post.getContent(),
                            post.getCreatedAt(),
                            null,
                            post.getParentId(),
                            post.getRootId(),
                            post.getReplyCount(),
                            post.getLikeCount(),
                            likedByMe);
                });

        PostResponse response = postService.createReply(authorId, parentId, new CreatePostRequest("reply root"));

        assertEquals(parentId, response.rootId());
    }

    @Test
    void createReplyThrowsNotFoundWhenParentDoesNotExist() {
        UUID authorId = UUID.fromString("d6a63297-3f0f-4da4-9014-097bb6ebcb55");
        UUID parentId = UUID.fromString("2fbf8f22-5485-4f95-a9c0-1723a49bfd7c");

        UserEntity author = new UserEntity();
        author.setId(authorId);
        author.setName("Eduardo");
        author.setRole(UserRole.USER);

        when(userRepository.findById(authorId)).thenReturn(Optional.of(author));
        when(postRepository.findById(parentId)).thenReturn(Optional.empty());

        ResourceNotFoundException exception = assertThrows(
                ResourceNotFoundException.class,
                () -> postService.createReply(authorId, parentId, new CreatePostRequest("reply")));

        assertEquals("Post not found", exception.getMessage());
        verify(postRepository, never()).save(any(PostEntity.class));
        verify(edgeRepository, never()).save(any(EdgeEntity.class));
    }

    @Test
    void getFeedReturnsItemsAndNextCursorWhenHasMore() {
        UUID authorOneId = UUID.fromString("d6a63297-3f0f-4da4-9014-097bb6ebcb55");
        UUID authorTwoId = UUID.fromString("338ed4fe-f51d-423d-8fd3-c35c4f740fc7");
        UUID firstPostId = UUID.fromString("2fbf8f22-5485-4f95-a9c0-1723a49bfd7c");
        UUID secondPostId = UUID.fromString("bbdd2f10-c028-4058-b7f6-f0e74f0ed78d");
        UUID thirdPostId = UUID.fromString("dc4a2480-0619-4512-8733-fb30bcf8dca8");

        PostEntity first = rootPost(firstPostId, authorOneId, "root-1", Instant.parse("2026-02-26T21:10:00Z"));
        PostEntity second = rootPost(secondPostId, authorTwoId, "root-2", Instant.parse("2026-02-26T21:09:00Z"));
        PostEntity third = rootPost(thirdPostId, authorOneId, "root-3", Instant.parse("2026-02-26T21:08:00Z"));

        when(postRepository.findRootPostsWithoutCursor(any(Pageable.class))).thenReturn(List.of(first, second, third));
        when(userRepository.findAllById(any())).thenReturn(List.of(
                author(authorOneId, "Author One"),
                author(authorTwoId, "Author Two")));
        when(topicDiscoveryService.deriveTopicKeywords(any())).thenReturn(List.of("graph", "context"));
        when(postMapper.toResponse(eq(first), eq("Author One"), isNull(), eq(UserRole.USER), eq(false),
                anyList()))
                .thenReturn(toResponse(first, "Author One", UserRole.USER, false));
        when(postMapper.toResponse(eq(second), eq("Author Two"), isNull(), eq(UserRole.USER), eq(true),
                anyList()))
                .thenReturn(toResponse(second, "Author Two", UserRole.USER, true));
        when(postLikeRepository.findLikedPostIdsByUserIdAndPostIdIn(eq(authorOneId), any()))
                .thenReturn(Set.of(secondPostId));

        FeedResponse response = postService.getFeed(null, 2, authorOneId, FeedSort.RECENT);

        assertEquals(2, response.items().size());
        assertEquals(firstPostId, response.items().get(0).id());
        assertEquals(secondPostId, response.items().get(1).id());
        assertEquals(encodeCursor("2026-02-26T21:09:00Z", secondPostId), response.nextCursor());
    }

    @Test
    void getFeedUsesCursorAndReturnsNullNextCursorWhenNoMoreItems() {
        UUID authorId = UUID.fromString("d6a63297-3f0f-4da4-9014-097bb6ebcb55");
        UUID cursorId = UUID.fromString("2fbf8f22-5485-4f95-a9c0-1723a49bfd7c");
        Instant cursorCreatedAt = Instant.parse("2026-02-26T21:10:00Z");
        String cursor = encodeCursor(cursorCreatedAt.toString(), cursorId);

        UUID postId = UUID.fromString("bbdd2f10-c028-4058-b7f6-f0e74f0ed78d");
        PostEntity post = rootPost(postId, authorId, "root-after", Instant.parse("2026-02-26T21:09:00Z"));

        when(postRepository.findRootPostsAfterCursor(eq(cursorCreatedAt), eq(cursorId), any(Pageable.class)))
                .thenReturn(List.of(post));
        when(userRepository.findAllById(any())).thenReturn(List.of(author(authorId, "Author One")));
        when(topicDiscoveryService.deriveTopicKeywords(any())).thenReturn(List.of("graph", "context"));
        when(postMapper.toResponse(eq(post), eq("Author One"), isNull(), eq(UserRole.USER), eq(false),
                anyList()))
                .thenReturn(toResponse(post, "Author One", UserRole.USER, false));

        FeedResponse response = postService.getFeed(cursor, 2, null, FeedSort.RECENT);

        assertEquals(1, response.items().size());
        assertEquals(postId, response.items().get(0).id());
        assertEquals(null, response.nextCursor());
    }

    @Test
    void getFeedThrowsBadRequestWhenCursorIsInvalid() {
        BadRequestException exception = assertThrows(
                BadRequestException.class,
                () -> postService.getFeed("invalid-cursor", 20, null, FeedSort.RECENT));
        assertEquals("Invalid cursor", exception.getMessage());
    }

    @Test
    void getFeedReturnsRelevantItemsAndNextCursorWhenHasMore() {
        UUID authorOneId = UUID.fromString("d6a63297-3f0f-4da4-9014-097bb6ebcb55");
        UUID authorTwoId = UUID.fromString("338ed4fe-f51d-423d-8fd3-c35c4f740fc7");
        UUID firstPostId = UUID.fromString("2fbf8f22-5485-4f95-a9c0-1723a49bfd7c");
        UUID secondPostId = UUID.fromString("bbdd2f10-c028-4058-b7f6-f0e74f0ed78d");
        UUID thirdPostId = UUID.fromString("dc4a2480-0619-4512-8733-fb30bcf8dca8");

        PostFeedProjection first = feedProjection(
                firstPostId, authorOneId, "root-1", Instant.parse("2026-02-26T21:10:00Z"), 4, 8,
                new BigDecimal("0.52"));
        PostFeedProjection second = feedProjection(
                secondPostId, authorTwoId, "root-2", Instant.parse("2026-02-26T21:09:00Z"), 2, 6,
                new BigDecimal("0.36"));
        PostFeedProjection third = feedProjection(
                thirdPostId, authorOneId, "root-3", Instant.parse("2026-02-26T21:08:00Z"), 0, 0,
                new BigDecimal("0.00"));

        when(postRepository.findRelevantRootPosts(eq(null), eq(null), eq(null), eq(3)))
                .thenReturn(List.of(first, second, third));
        when(userRepository.findAllById(any())).thenReturn(List.of(
                author(authorOneId, "Author One"),
                author(authorTwoId, "Author Two")));
        when(topicDiscoveryService.deriveTopicKeywords(any())).thenReturn(List.of("graph", "ranking"));
        when(postLikeRepository.findLikedPostIdsByUserIdAndPostIdIn(eq(authorOneId), any()))
                .thenReturn(Set.of(secondPostId));

        FeedResponse response = postService.getFeed(null, 2, authorOneId, FeedSort.RELEVANT);

        assertEquals(2, response.items().size());
        assertEquals(firstPostId, response.items().get(0).id());
        assertEquals(secondPostId, response.items().get(1).id());
        assertEquals(encodeRelevantCursor("0.36", "2026-02-26T21:09:00Z", secondPostId), response.nextCursor());
    }

    @Test
    void likePostCreatesLikeAndIncrementsCounter() {
        UUID userId = UUID.fromString("d6a63297-3f0f-4da4-9014-097bb6ebcb55");
        UUID postId = UUID.fromString("2fbf8f22-5485-4f95-a9c0-1723a49bfd7c");
        UUID authorId = UUID.fromString("338ed4fe-f51d-423d-8fd3-c35c4f740fc7");

        UserEntity user = author(userId, "Viewer");
        PostEntity post = rootPost(postId, authorId, "post", Instant.parse("2026-02-26T21:10:00Z"));
        post.setLikeCount(0);

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(postRepository.findById(postId)).thenReturn(Optional.of(post));
        when(postLikeRepository.existsByPostIdAndUserId(postId, userId)).thenReturn(false);
        when(postLikeRepository.save(any(PostLikeEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(postRepository.save(any(PostEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(userRepository.findById(authorId)).thenReturn(Optional.of(author(authorId, "Author")));
        when(topicDiscoveryService.deriveTopicKeywords(any())).thenReturn(List.of("graph", "signal"));
        when(postMapper.toResponse(eq(post), eq("Author"), isNull(), eq(UserRole.USER), eq(true), anyList()))
                .thenAnswer(invocation -> toResponse(post, "Author", UserRole.USER, true));

        PostResponse response = postService.likePost(userId, postId);

        assertEquals(1, post.getLikeCount());
        assertEquals(1, response.likeCount());
        assertEquals(true, response.likedByMe());
    }

    @Test
    void unlikePostRemovesLikeAndDecrementsCounter() {
        UUID userId = UUID.fromString("d6a63297-3f0f-4da4-9014-097bb6ebcb55");
        UUID postId = UUID.fromString("2fbf8f22-5485-4f95-a9c0-1723a49bfd7c");
        UUID authorId = UUID.fromString("338ed4fe-f51d-423d-8fd3-c35c4f740fc7");

        UserEntity user = author(userId, "Viewer");
        PostEntity post = rootPost(postId, authorId, "post", Instant.parse("2026-02-26T21:10:00Z"));
        post.setLikeCount(2);
        PostLikeEntity like = new PostLikeEntity();
        like.setId(UUID.fromString("11111111-1111-1111-1111-111111111111"));
        like.setPostId(postId);
        like.setUserId(userId);

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(postRepository.findById(postId)).thenReturn(Optional.of(post));
        when(postLikeRepository.findByPostIdAndUserId(postId, userId)).thenReturn(Optional.of(like));
        when(postRepository.save(any(PostEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(userRepository.findById(authorId)).thenReturn(Optional.of(author(authorId, "Author")));
        when(topicDiscoveryService.deriveTopicKeywords(any())).thenReturn(List.of("graph", "signal"));
        when(postMapper.toResponse(eq(post), eq("Author"), isNull(), eq(UserRole.USER), eq(false), anyList()))
                .thenAnswer(invocation -> toResponse(post, "Author", UserRole.USER, false));

        PostResponse response = postService.unlikePost(userId, postId);

        assertEquals(1, post.getLikeCount());
        assertEquals(1, response.likeCount());
        assertEquals(false, response.likedByMe());
        verify(postLikeRepository).delete(eq(like));
    }

    @Test
    void updatePostThrowsForbiddenWhenActorIsNotAuthor() {
        UUID actorId = UUID.fromString("d6a63297-3f0f-4da4-9014-097bb6ebcb55");
        UUID authorId = UUID.fromString("338ed4fe-f51d-423d-8fd3-c35c4f740fc7");
        UUID postId = UUID.fromString("2fbf8f22-5485-4f95-a9c0-1723a49bfd7c");

        UserEntity actor = author(actorId, "Actor User");
        PostEntity post = rootPost(postId, authorId, "original", Instant.parse("2026-02-26T21:10:00Z"));

        when(userRepository.findById(actorId)).thenReturn(Optional.of(actor));
        when(postRepository.findById(postId)).thenReturn(Optional.of(post));

        ForbiddenException exception = assertThrows(
                ForbiddenException.class,
                () -> postService.updatePost(actorId, postId, new UpdatePostRequest("updated")));

        assertEquals("You can only edit your own posts", exception.getMessage());
        verify(postRepository, never()).save(any(PostEntity.class));
    }

    @Test
    void deletePostAllowsModeratorToDeleteOtherAuthorPostWithoutReplies() {
        UUID modId = UUID.fromString("d6a63297-3f0f-4da4-9014-097bb6ebcb55");
        UUID authorId = UUID.fromString("338ed4fe-f51d-423d-8fd3-c35c4f740fc7");
        UUID parentId = UUID.fromString("dc4a2480-0619-4512-8733-fb30bcf8dca8");
        UUID postId = UUID.fromString("2fbf8f22-5485-4f95-a9c0-1723a49bfd7c");

        UserEntity moderator = author(modId, "Moderator");
        moderator.setRole(UserRole.MOD);
        PostEntity post = rootPost(postId, authorId, "reply", Instant.parse("2026-02-26T21:10:00Z"));
        post.setParentId(parentId);

        PostEntity parent = rootPost(parentId, authorId, "parent", Instant.parse("2026-02-26T21:00:00Z"));
        parent.setReplyCount(3);

        when(userRepository.findById(modId)).thenReturn(Optional.of(moderator));
        when(postRepository.findById(postId)).thenReturn(Optional.of(post));
        when(postRepository.existsByParentId(postId)).thenReturn(false);
        when(postRepository.findById(parentId)).thenReturn(Optional.of(parent));

        postService.deletePost(modId, postId);

        assertEquals(2, parent.getReplyCount());
        verify(edgeRepository).deleteBySourcePostId(postId);
        verify(postLikeRepository).deleteByPostId(postId);
        verify(postRepository).delete(post);
        verify(auditLogService).log(eq(modId), eq("POST_DELETED"), eq("POST"), eq(postId), any());
    }

    @Test
    void deletePostThrowsConflictWhenPostHasReplies() {
        UUID authorId = UUID.fromString("d6a63297-3f0f-4da4-9014-097bb6ebcb55");
        UUID postId = UUID.fromString("2fbf8f22-5485-4f95-a9c0-1723a49bfd7c");

        UserEntity author = author(authorId, "Author");
        PostEntity post = rootPost(postId, authorId, "root", Instant.parse("2026-02-26T21:10:00Z"));

        when(userRepository.findById(authorId)).thenReturn(Optional.of(author));
        when(postRepository.findById(postId)).thenReturn(Optional.of(post));
        when(postRepository.existsByParentId(postId)).thenReturn(true);

        ConflictException exception = assertThrows(
                ConflictException.class,
                () -> postService.deletePost(authorId, postId));

        assertEquals("Cannot delete post with replies", exception.getMessage());
        verify(postRepository, never()).delete(any(PostEntity.class));
    }

    private PostEntity rootPost(UUID id, UUID authorId, String content, Instant createdAt) {
        PostEntity post = new PostEntity();
        post.setId(id);
        post.setAuthorId(authorId);
        post.setContent(content);
        post.setCreatedAt(createdAt);
        post.setParentId(null);
        post.setRootId(id);
        post.setReplyCount(0);
        post.setLikeCount(0);
        return post;
    }

    private UserEntity author(UUID id, String name) {
        UserEntity user = new UserEntity();
        user.setId(id);
        user.setName(name);
        user.setEmail(name.toLowerCase().replace(" ", "") + "@example.com");
        user.setPasswordHash("hash");
        user.setRole(UserRole.USER);
        user.setCreatedAt(Instant.parse("2026-02-26T21:10:00Z"));
        return user;
    }

    private PostResponse toResponse(PostEntity post, String authorName, UserRole authorRole, boolean likedByMe) {
        return new PostResponse(
                post.getId(),
                post.getAuthorId(),
                authorName,
                "",
                authorRole,
                post.getContent(),
                post.getCreatedAt(),
                null,
                post.getParentId(),
                post.getRootId(),
                post.getReplyCount(),
                post.getLikeCount(),
                likedByMe);
    }

    private String encodeCursor(String createdAt, UUID id) {
        String raw = createdAt + "|" + id;
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    private String encodeRelevantCursor(String score, String createdAt, UUID id) {
        String raw = score + "|" + createdAt + "|" + id;
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    private PostFeedProjection feedProjection(UUID id,
            UUID authorId,
            String content,
            Instant createdAt,
            int replyCount,
            int likeCount,
            BigDecimal feedScore) {
        return new PostFeedProjection() {
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
                return id;
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
                return BigDecimal.valueOf(0.72);
            }
        };
    }
}
