package io.github.eduardopaim.graphcognitio.posts.service;

import io.github.eduardopaim.graphcognitio.entity.PostEntity;
import io.github.eduardopaim.graphcognitio.entity.UserEntity;
import io.github.eduardopaim.graphcognitio.exception.ResourceNotFoundException;
import io.github.eduardopaim.graphcognitio.exception.UnauthorizedException;
import io.github.eduardopaim.graphcognitio.posts.dto.CreatePostRequest;
import io.github.eduardopaim.graphcognitio.posts.dto.PostResponse;
import io.github.eduardopaim.graphcognitio.posts.mapper.PostMapper;
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
    private final PostMapper postMapper;

    public PostService(PostRepository postRepository,
                       UserRepository userRepository,
                       PostMapper postMapper) {
        this.postRepository = postRepository;
        this.userRepository = userRepository;
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

    @Transactional(readOnly = true)
    public PostResponse getPostById(UUID postId) {
        PostEntity post = postRepository.findById(postId)
                .orElseThrow(() -> new ResourceNotFoundException("Post not found"));

        String authorName = userRepository.findById(post.getAuthorId())
                .map(UserEntity::getName)
                .orElseThrow(() -> new ResourceNotFoundException("Author not found"));

        return postMapper.toResponse(post, authorName);
    }
}
