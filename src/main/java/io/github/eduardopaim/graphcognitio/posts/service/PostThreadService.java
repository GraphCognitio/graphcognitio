package io.github.eduardopaim.graphcognitio.posts.service;

import io.github.eduardopaim.graphcognitio.entity.UserRole;
import io.github.eduardopaim.graphcognitio.entity.PostEntity;
import io.github.eduardopaim.graphcognitio.exception.ResourceNotFoundException;
import io.github.eduardopaim.graphcognitio.posts.dto.PostResponse;
import io.github.eduardopaim.graphcognitio.posts.dto.PostThreadResponse;
import io.github.eduardopaim.graphcognitio.posts.dto.ThreadNodeResponse;
import io.github.eduardopaim.graphcognitio.posts.mapper.PostMapper;
import io.github.eduardopaim.graphcognitio.repository.PostLikeRepository;
import io.github.eduardopaim.graphcognitio.repository.PostRepository;
import io.github.eduardopaim.graphcognitio.repository.UserRepository;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PostThreadService {

    private final PostRepository postRepository;
    private final UserRepository userRepository;
    private final PostLikeRepository postLikeRepository;
    private final PostMapper postMapper;
    private final TopicDiscoveryService topicDiscoveryService;

    public PostThreadService(PostRepository postRepository,
            UserRepository userRepository,
            PostLikeRepository postLikeRepository,
            PostMapper postMapper,
            TopicDiscoveryService topicDiscoveryService) {
        this.postRepository = postRepository;
        this.userRepository = userRepository;
        this.postLikeRepository = postLikeRepository;
        this.postMapper = postMapper;
        this.topicDiscoveryService = topicDiscoveryService;
    }

    @Transactional(readOnly = true)
    public PostThreadResponse getThread(UUID postId, UUID viewerId, int depth) {
        PostEntity focusPost = postRepository.findById(postId)
                .orElseThrow(() -> new ResourceNotFoundException("Post not found"));

        List<PostEntity> threadPosts = postRepository.findByRootIdOrderByCreatedAtAscIdAsc(focusPost.getRootId());
        if (threadPosts.isEmpty()) {
            throw new ResourceNotFoundException("Thread not found");
        }

        Map<UUID, PostEntity> postById = threadPosts.stream()
                .collect(Collectors.toMap(PostEntity::getId, post -> post, (first, second) -> first,
                        LinkedHashMap::new));

        Map<UUID, List<PostEntity>> repliesByParentId = new LinkedHashMap<>();
        for (PostEntity post : threadPosts) {
            if (post.getParentId() == null) {
                continue;
            }
            repliesByParentId.computeIfAbsent(post.getParentId(), ignored -> new ArrayList<>()).add(post);
        }

        Map<UUID, AuthorDetails> authorDetails = loadAuthorDetails(threadPosts);
        Set<UUID> likedPostIds = loadLikedPostIds(viewerId, threadPosts);

        List<PostResponse> ancestors = buildAncestors(focusPost, postById, authorDetails, likedPostIds);
        PostResponse focus = toResponse(focusPost, authorDetails, likedPostIds);
        List<ThreadNodeResponse> replies = buildReplies(focusPost.getId(), 1, depth, repliesByParentId, authorDetails,
                likedPostIds);

        return new PostThreadResponse(ancestors, focus, replies);
    }

    private List<PostResponse> buildAncestors(PostEntity focusPost,
            Map<UUID, PostEntity> postById,
            Map<UUID, AuthorDetails> authorDetails,
            Set<UUID> likedPostIds) {
        List<PostResponse> ancestors = new ArrayList<>();
        PostEntity current = focusPost;

        while (current.getParentId() != null) {
            PostEntity parent = postById.get(current.getParentId());
            if (parent == null) {
                throw new ResourceNotFoundException("Parent post not found");
            }
            ancestors.add(toResponse(parent, authorDetails, likedPostIds));
            current = parent;
        }

        Collections.reverse(ancestors);
        return ancestors;
    }

    private List<ThreadNodeResponse> buildReplies(UUID parentId,
            int currentDepth,
            int maxDepth,
            Map<UUID, List<PostEntity>> repliesByParentId,
            Map<UUID, AuthorDetails> authorDetails,
            Set<UUID> likedPostIds) {
        if (currentDepth > maxDepth) {
            return List.of();
        }

        List<PostEntity> replies = repliesByParentId.getOrDefault(parentId, List.of());
        return replies.stream()
                .map(reply -> new ThreadNodeResponse(
                        toResponse(reply, authorDetails, likedPostIds),
                        buildReplies(reply.getId(), currentDepth + 1, maxDepth, repliesByParentId, authorDetails,
                                likedPostIds)))
                .toList();
    }

    private PostResponse toResponse(PostEntity post, Map<UUID, AuthorDetails> authorDetails, Set<UUID> likedPostIds) {
        AuthorDetails author = authorDetails.get(post.getAuthorId());
        return postMapper.toResponse(
                post,
                author.name(),
                author.username(),
                author.role(),
                likedPostIds.contains(post.getId()),
                topicDiscoveryService.deriveTopicKeywords(post.getContent()));
    }

    private Map<UUID, AuthorDetails> loadAuthorDetails(List<PostEntity> posts) {
        Set<UUID> authorIds = posts.stream()
                .map(PostEntity::getAuthorId)
                .collect(Collectors.toSet());

        Map<UUID, AuthorDetails> authorDetails = new HashMap<>();
        userRepository.findAllById(authorIds)
                .forEach(author -> authorDetails.put(author.getId(),
                        new AuthorDetails(author.getName(), author.getUsername(), author.getRole())));

        for (UUID authorId : authorIds) {
            if (!authorDetails.containsKey(authorId)) {
                throw new ResourceNotFoundException("Author not found");
            }
        }
        return authorDetails;
    }

    private Set<UUID> loadLikedPostIds(UUID viewerId, List<PostEntity> posts) {
        if (viewerId == null || posts.isEmpty()) {
            return Set.of();
        }

        Set<UUID> postIds = posts.stream()
                .map(PostEntity::getId)
                .collect(Collectors.toSet());
        return postLikeRepository.findLikedPostIdsByUserIdAndPostIdIn(viewerId, postIds);
    }

    private record AuthorDetails(String name, String username, UserRole role) {
    }
}
