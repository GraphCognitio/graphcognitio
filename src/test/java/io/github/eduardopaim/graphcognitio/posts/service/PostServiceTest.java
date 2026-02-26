package io.github.eduardopaim.graphcognitio.posts.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.eduardopaim.graphcognitio.entity.EdgeEntity;
import io.github.eduardopaim.graphcognitio.entity.EdgeType;
import io.github.eduardopaim.graphcognitio.entity.PostEntity;
import io.github.eduardopaim.graphcognitio.entity.UserEntity;
import io.github.eduardopaim.graphcognitio.exception.ResourceNotFoundException;
import io.github.eduardopaim.graphcognitio.posts.dto.CreatePostRequest;
import io.github.eduardopaim.graphcognitio.posts.dto.PostResponse;
import io.github.eduardopaim.graphcognitio.posts.mapper.PostMapper;
import io.github.eduardopaim.graphcognitio.repository.EdgeRepository;
import io.github.eduardopaim.graphcognitio.repository.PostRepository;
import io.github.eduardopaim.graphcognitio.repository.UserRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PostServiceTest {

    @Mock
    private PostRepository postRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private EdgeRepository edgeRepository;

    @Mock
    private PostMapper postMapper;

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
        when(postMapper.toResponse(any(PostEntity.class), eq("Eduardo")))
                .thenAnswer(invocation -> {
                    PostEntity post = invocation.getArgument(0);
                    return new PostResponse(
                            post.getId(),
                            post.getAuthorId(),
                            "Eduardo",
                            post.getContent(),
                            post.getCreatedAt(),
                            post.getParentId(),
                            post.getRootId(),
                            post.getReplyCount()
                    );
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

        PostEntity parent = new PostEntity();
        parent.setId(parentId);
        parent.setParentId(null);
        parent.setRootId(parentId);
        parent.setReplyCount(0);

        when(userRepository.findById(authorId)).thenReturn(Optional.of(author));
        when(postRepository.findById(parentId)).thenReturn(Optional.of(parent));
        when(postRepository.save(any(PostEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(edgeRepository.save(any(EdgeEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(postMapper.toResponse(any(PostEntity.class), eq("Eduardo")))
                .thenAnswer(invocation -> {
                    PostEntity post = invocation.getArgument(0);
                    return new PostResponse(
                            post.getId(),
                            post.getAuthorId(),
                            "Eduardo",
                            post.getContent(),
                            post.getCreatedAt(),
                            post.getParentId(),
                            post.getRootId(),
                            post.getReplyCount()
                    );
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

        when(userRepository.findById(authorId)).thenReturn(Optional.of(author));
        when(postRepository.findById(parentId)).thenReturn(Optional.empty());

        ResourceNotFoundException exception = assertThrows(
                ResourceNotFoundException.class,
                () -> postService.createReply(authorId, parentId, new CreatePostRequest("reply"))
        );

        assertEquals("Post not found", exception.getMessage());
        verify(postRepository, never()).save(any(PostEntity.class));
        verify(edgeRepository, never()).save(any(EdgeEntity.class));
    }
}
