package io.github.eduardopaim.graphcognitio.posts.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import io.github.eduardopaim.graphcognitio.entity.PostEntity;
import io.github.eduardopaim.graphcognitio.entity.UserEntity;
import io.github.eduardopaim.graphcognitio.entity.UserRole;
import io.github.eduardopaim.graphcognitio.posts.dto.PostResponse;
import io.github.eduardopaim.graphcognitio.posts.dto.PostThreadResponse;
import io.github.eduardopaim.graphcognitio.posts.mapper.PostMapper;
import io.github.eduardopaim.graphcognitio.repository.PostLikeRepository;
import io.github.eduardopaim.graphcognitio.repository.PostRepository;
import io.github.eduardopaim.graphcognitio.repository.UserRepository;
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
class PostThreadServiceTest {

    @Mock
    private PostRepository postRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private PostLikeRepository postLikeRepository;

    @Mock
    private PostMapper postMapper;

    @Mock
    private TopicDiscoveryService topicDiscoveryService;

    @InjectMocks
    private PostThreadService postThreadService;

    @Test
    void getThreadReturnsAncestorsAndNestedReplies() {
        UUID viewerId = UUID.fromString("d6a63297-3f0f-4da4-9014-097bb6ebcb55");
        UUID rootId = UUID.fromString("2fbf8f22-5485-4f95-a9c0-1723a49bfd7c");
        UUID focusId = UUID.fromString("bbdd2f10-c028-4058-b7f6-f0e74f0ed78d");
        UUID childId = UUID.fromString("dc4a2480-0619-4512-8733-fb30bcf8dca8");
        UUID grandchildId = UUID.fromString("e76c72b6-c874-4b0a-bf85-6ac5b74ff7b6");
        UUID authorId = UUID.fromString("338ed4fe-f51d-423d-8fd3-c35c4f740fc7");

        PostEntity root = post(rootId, authorId, "root", Instant.parse("2026-02-26T21:00:00Z"), null, rootId);
        PostEntity focus = post(focusId, authorId, "focus", Instant.parse("2026-02-26T21:05:00Z"), rootId, rootId);
        PostEntity child = post(childId, authorId, "child", Instant.parse("2026-02-26T21:10:00Z"), focusId, rootId);
        PostEntity grandchild = post(grandchildId, authorId, "grandchild", Instant.parse("2026-02-26T21:15:00Z"),
                childId, rootId);

        when(postRepository.findById(focusId)).thenReturn(Optional.of(focus));
        when(postRepository.findByRootIdOrderByCreatedAtAscIdAsc(rootId))
                .thenReturn(List.of(root, focus, child, grandchild));
        when(userRepository.findAllById(any())).thenReturn(List.of(author(authorId, "Eduardo")));
        when(postLikeRepository.findLikedPostIdsByUserIdAndPostIdIn(eq(viewerId), any())).thenReturn(Set.of(childId));
        when(topicDiscoveryService.deriveTopicKeywords(any())).thenReturn(List.of("graph", "conversation"));
        when(postMapper.toResponse(any(PostEntity.class), eq("Eduardo"), any(String.class), eq(UserRole.USER),
                anyBoolean(), anyList()))
                .thenAnswer(invocation -> {
                    PostEntity post = invocation.getArgument(0);
                    boolean likedByMe = invocation.getArgument(4);
                    return toResponse(post, "Eduardo", UserRole.USER, likedByMe);
                });

        PostThreadResponse response = postThreadService.getThread(focusId, viewerId, 2);

        assertEquals(1, response.ancestors().size());
        assertEquals(rootId, response.ancestors().get(0).id());
        assertEquals(focusId, response.focus().id());
        assertEquals(1, response.replies().size());
        assertEquals(childId, response.replies().get(0).post().id());
        assertTrue(response.replies().get(0).post().likedByMe());
        assertEquals(1, response.replies().get(0).replies().size());
        assertEquals(grandchildId, response.replies().get(0).replies().get(0).post().id());
    }

    @Test
    void getThreadHonorsDepthLimit() {
        UUID rootId = UUID.fromString("2fbf8f22-5485-4f95-a9c0-1723a49bfd7c");
        UUID focusId = UUID.fromString("bbdd2f10-c028-4058-b7f6-f0e74f0ed78d");
        UUID childId = UUID.fromString("dc4a2480-0619-4512-8733-fb30bcf8dca8");
        UUID grandchildId = UUID.fromString("e76c72b6-c874-4b0a-bf85-6ac5b74ff7b6");
        UUID authorId = UUID.fromString("338ed4fe-f51d-423d-8fd3-c35c4f740fc7");

        PostEntity root = post(rootId, authorId, "root", Instant.parse("2026-02-26T21:00:00Z"), null, rootId);
        PostEntity focus = post(focusId, authorId, "focus", Instant.parse("2026-02-26T21:05:00Z"), rootId, rootId);
        PostEntity child = post(childId, authorId, "child", Instant.parse("2026-02-26T21:10:00Z"), focusId, rootId);
        PostEntity grandchild = post(grandchildId, authorId, "grandchild", Instant.parse("2026-02-26T21:15:00Z"),
                childId, rootId);

        when(postRepository.findById(focusId)).thenReturn(Optional.of(focus));
        when(postRepository.findByRootIdOrderByCreatedAtAscIdAsc(rootId))
                .thenReturn(List.of(root, focus, child, grandchild));
        when(userRepository.findAllById(any())).thenReturn(List.of(author(authorId, "Eduardo")));
        when(topicDiscoveryService.deriveTopicKeywords(any())).thenReturn(List.of("graph", "conversation"));
        when(postMapper.toResponse(any(PostEntity.class), eq("Eduardo"), any(String.class), eq(UserRole.USER),
                anyBoolean(), anyList()))
                .thenAnswer(invocation -> {
                    PostEntity post = invocation.getArgument(0);
                    boolean likedByMe = invocation.getArgument(4);
                    return toResponse(post, "Eduardo", UserRole.USER, likedByMe);
                });

        PostThreadResponse response = postThreadService.getThread(focusId, null, 1);

        assertEquals(1, response.replies().size());
        assertEquals(childId, response.replies().get(0).post().id());
        assertEquals(0, response.replies().get(0).replies().size());
    }

    private PostEntity post(UUID id, UUID authorId, String content, Instant createdAt, UUID parentId, UUID rootId) {
        PostEntity post = new PostEntity();
        post.setId(id);
        post.setAuthorId(authorId);
        post.setContent(content);
        post.setCreatedAt(createdAt);
        post.setParentId(parentId);
        post.setRootId(rootId);
        post.setReplyCount(0);
        post.setLikeCount(0);
        return post;
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
}
