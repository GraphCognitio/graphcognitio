package io.github.eduardopaim.graphcognitio.posts.service;

import io.github.eduardopaim.graphcognitio.entity.PostEntity;
import io.github.eduardopaim.graphcognitio.entity.UserRole;
import io.github.eduardopaim.graphcognitio.exception.ResourceNotFoundException;
import io.github.eduardopaim.graphcognitio.posts.dto.RelatedPostResponse;
import io.github.eduardopaim.graphcognitio.repository.PostLikeRepository;
import io.github.eduardopaim.graphcognitio.repository.PostRepository;
import io.github.eduardopaim.graphcognitio.repository.UserRepository;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RelatedPostsService {

    private final PostRepository postRepository;
    private final UserRepository userRepository;
    private final PostLikeRepository postLikeRepository;
    private final TopicDiscoveryService topicDiscoveryService;

    public RelatedPostsService(PostRepository postRepository,
            UserRepository userRepository,
            PostLikeRepository postLikeRepository,
            TopicDiscoveryService topicDiscoveryService) {
        this.postRepository = postRepository;
        this.userRepository = userRepository;
        this.postLikeRepository = postLikeRepository;
        this.topicDiscoveryService = topicDiscoveryService;
    }

    @Transactional(readOnly = true)
    public List<RelatedPostResponse> getRelatedPosts(UUID postId, UUID viewerId, int limit) {
        PostEntity seedPost = postRepository.findById(postId)
                .orElseThrow(() -> new ResourceNotFoundException("Post not found"));

        List<TopicMatchResult> relatedPosts = topicDiscoveryService.findRelatedRootPosts(seedPost, limit);

        Map<UUID, AuthorDetails> authorDetails = loadAuthorDetails(relatedPosts);
        Set<UUID> likedPostIds = loadLikedPostIds(viewerId, relatedPosts);

        return relatedPosts.stream()
                .map(match -> new RelatedPostResponse(
                        match.projection().getId(),
                        match.projection().getAuthorId(),
                        authorDetails.get(match.projection().getAuthorId()).name(),
                        authorDetails.get(match.projection().getAuthorId()).role(),
                        match.projection().getContent(),
                        match.projection().getCreatedAt(),
                        match.projection().getUpdatedAt(),
                        match.projection().getParentId(),
                        match.projection().getRootId(),
                        match.projection().getReplyCount(),
                        match.projection().getLikeCount(),
                        likedPostIds.contains(match.projection().getId()),
                        match.topicScore(),
                        match.matchedTerms(),
                        match.topicReason()))
                .toList();
    }

    private Map<UUID, AuthorDetails> loadAuthorDetails(List<TopicMatchResult> posts) {
        Set<UUID> authorIds = posts.stream()
                .map(post -> post.projection().getAuthorId())
                .collect(Collectors.toSet());

        if (authorIds.isEmpty()) {
            return Map.of();
        }

        Map<UUID, AuthorDetails> authorDetails = new HashMap<>();
        userRepository.findAllById(authorIds)
                .forEach(author -> authorDetails.put(author.getId(),
                        new AuthorDetails(author.getName(), author.getRole())));

        for (UUID authorId : authorIds) {
            if (!authorDetails.containsKey(authorId)) {
                throw new ResourceNotFoundException("Author not found");
            }
        }

        return authorDetails;
    }

    private Set<UUID> loadLikedPostIds(UUID viewerId, List<TopicMatchResult> posts) {
        if (viewerId == null || posts.isEmpty()) {
            return Set.of();
        }

        Set<UUID> postIds = posts.stream()
                .map(post -> post.projection().getId())
                .collect(Collectors.toSet());
        return postLikeRepository.findLikedPostIdsByUserIdAndPostIdIn(viewerId, postIds);
    }

    private record AuthorDetails(String name, UserRole role) {
    }
}
