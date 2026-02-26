package io.github.eduardopaim.graphcognitio.posts.service;

import io.github.eduardopaim.graphcognitio.entity.EdgeEntity;
import io.github.eduardopaim.graphcognitio.entity.EdgeType;
import io.github.eduardopaim.graphcognitio.entity.PostEntity;
import io.github.eduardopaim.graphcognitio.entity.UserEntity;
import io.github.eduardopaim.graphcognitio.exception.ResourceNotFoundException;
import io.github.eduardopaim.graphcognitio.exception.UnauthorizedException;
import io.github.eduardopaim.graphcognitio.posts.dto.CreatePostRequest;
import io.github.eduardopaim.graphcognitio.posts.dto.PostResponse;
import io.github.eduardopaim.graphcognitio.posts.mapper.PostMapper;
import io.github.eduardopaim.graphcognitio.repository.EdgeRepository;
import io.github.eduardopaim.graphcognitio.repository.PostRepository;
import io.github.eduardopaim.graphcognitio.repository.UserRepository;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PostService {

    private final PostRepository postRepository;
    private final UserRepository userRepository;
    private final EdgeRepository edgeRepository;
    private final PostMapper postMapper;

    public PostService(PostRepository postRepository,
                       UserRepository userRepository,
                       EdgeRepository edgeRepository,
                       PostMapper postMapper) {
        this.postRepository = postRepository;
        this.userRepository = userRepository;
        this.edgeRepository = edgeRepository;
        this.postMapper = postMapper;
    }

    @Transactional
    public PostResponse createRootPost(UUID authorId, CreatePostRequest request) {
        UserEntity author = userRepository.findById(authorId)
                .orElseThrow(() -> new UnauthorizedException("Authenticated user not found"));

        UUID postId = UUID.randomUUID();
        PostEntity post = new PostEntity();
        post.setId(postId);
        post.setAuthorId(authorId);
        post.setContent(request.content().trim());
        post.setCreatedAt(Instant.now());
        post.setParentId(null);
        post.setRootId(postId);
        post.setReplyCount(0);

        PostEntity saved = postRepository.save(post);
        return postMapper.toResponse(saved, author.getName());
    }

    @Transactional
    public PostResponse createReply(UUID authorId, UUID parentPostId, CreatePostRequest request) {
        UserEntity author = userRepository.findById(authorId)
                .orElseThrow(() -> new UnauthorizedException("Authenticated user not found"));

        PostEntity parentPost = postRepository.findById(parentPostId)
                .orElseThrow(() -> new ResourceNotFoundException("Post not found"));

        PostEntity replyPost = new PostEntity();
        replyPost.setId(UUID.randomUUID());
        replyPost.setAuthorId(authorId);
        replyPost.setContent(request.content().trim());
        replyPost.setCreatedAt(Instant.now());
        replyPost.setParentId(parentPost.getId());
        replyPost.setRootId(resolveRootId(parentPost));
        replyPost.setReplyCount(0);

        PostEntity savedReply = postRepository.save(replyPost);

        parentPost.setReplyCount(parentPost.getReplyCount() + 1);
        postRepository.save(parentPost);

        EdgeEntity edge = new EdgeEntity();
        edge.setSourcePostId(savedReply.getId());
        edge.setTargetPostId(parentPost.getId());
        edge.setType(EdgeType.REPLY);
        edge.setCreatedAt(Instant.now());
        edgeRepository.save(edge);

        return postMapper.toResponse(savedReply, author.getName());
    }

    @Transactional(readOnly = true)
    public PostResponse getPostById(UUID postId) {
        PostEntity post = postRepository.findById(postId)
                .orElseThrow(() -> new ResourceNotFoundException("Post not found"));

        String authorName = userRepository.findById(post.getAuthorId())
                .map(UserEntity::getName)
                .orElseThrow(() -> new ResourceNotFoundException("Author not found"));

        return postMapper.toResponse(post, authorName);
    }

    private UUID resolveRootId(PostEntity parentPost) {
        if (parentPost.getParentId() == null) {
            return parentPost.getId();
        }
        return parentPost.getRootId();
    }
}
