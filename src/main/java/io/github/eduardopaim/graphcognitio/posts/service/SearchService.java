package io.github.eduardopaim.graphcognitio.posts.service;

import io.github.eduardopaim.graphcognitio.exception.BadRequestException;
import io.github.eduardopaim.graphcognitio.exception.ResourceNotFoundException;
import io.github.eduardopaim.graphcognitio.posts.dto.FeedResponse;
import io.github.eduardopaim.graphcognitio.posts.dto.PostResponse;
import io.github.eduardopaim.graphcognitio.repository.PostLikeRepository;
import io.github.eduardopaim.graphcognitio.repository.PostRepository;
import io.github.eduardopaim.graphcognitio.repository.PostSearchProjection;
import io.github.eduardopaim.graphcognitio.repository.UserRepository;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class SearchService {

    private final PostRepository postRepository;
    private final UserRepository userRepository;
    private final PostLikeRepository postLikeRepository;

    public SearchService(PostRepository postRepository,
                         UserRepository userRepository,
                         PostLikeRepository postLikeRepository) {
        this.postRepository = postRepository;
        this.userRepository = userRepository;
        this.postLikeRepository = postLikeRepository;
    }

    @Transactional(readOnly = true)
    public FeedResponse search(String query, String cursor, int limit, UUID viewerId) {
        String normalizedQuery = normalizeQuery(query);
        SearchCursorKey cursorKey = decodeCursor(cursor);

        List<PostSearchProjection> page = postRepository.searchPosts(
                normalizedQuery,
                cursorKey == null ? null : cursorKey.score(),
                cursorKey == null ? null : cursorKey.createdAt(),
                cursorKey == null ? null : cursorKey.id(),
                limit + 1
        );

        boolean hasMore = page.size() > limit;
        List<PostSearchProjection> currentItems = hasMore ? new ArrayList<>(page.subList(0, limit)) : page;
        Map<UUID, String> authorNames = loadAuthorNames(currentItems);
        Set<UUID> likedPostIds = loadLikedPostIds(viewerId, currentItems);

        List<PostResponse> items = currentItems.stream()
                .map(post -> new PostResponse(
                        post.getId(),
                        post.getAuthorId(),
                        authorNames.get(post.getAuthorId()),
                        post.getContent(),
                        post.getCreatedAt(),
                        post.getParentId(),
                        post.getRootId(),
                        post.getReplyCount(),
                        post.getLikeCount(),
                        likedPostIds.contains(post.getId())
                ))
                .toList();

        String nextCursor = null;
        if (hasMore && !currentItems.isEmpty()) {
            PostSearchProjection lastPost = currentItems.get(currentItems.size() - 1);
            nextCursor = encodeCursor(lastPost.getSearchScore(), lastPost.getCreatedAt(), lastPost.getId());
        }

        return new FeedResponse(items, nextCursor);
    }

    private String normalizeQuery(String query) {
        if (!StringUtils.hasText(query)) {
            throw new BadRequestException("Search query cannot be blank");
        }
        return query.trim();
    }

    private Map<UUID, String> loadAuthorNames(List<PostSearchProjection> posts) {
        Set<UUID> authorIds = posts.stream()
                .map(PostSearchProjection::getAuthorId)
                .collect(Collectors.toSet());

        if (authorIds.isEmpty()) {
            return Map.of();
        }

        Map<UUID, String> authorNames = new HashMap<>();
        userRepository.findAllById(authorIds).forEach(author -> authorNames.put(author.getId(), author.getName()));

        for (UUID authorId : authorIds) {
            if (!authorNames.containsKey(authorId)) {
                throw new ResourceNotFoundException("Author not found");
            }
        }

        return authorNames;
    }

    private Set<UUID> loadLikedPostIds(UUID viewerId, List<PostSearchProjection> posts) {
        if (viewerId == null || posts.isEmpty()) {
            return Set.of();
        }

        Set<UUID> postIds = posts.stream()
                .map(PostSearchProjection::getId)
                .collect(Collectors.toSet());

        return postLikeRepository.findLikedPostIdsByUserIdAndPostIdIn(viewerId, postIds);
    }

    private String encodeCursor(BigDecimal score, Instant createdAt, UUID id) {
        String raw = score.toPlainString() + "|" + createdAt + "|" + id;
        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    private SearchCursorKey decodeCursor(String cursor) {
        if (!StringUtils.hasText(cursor)) {
            return null;
        }

        try {
            String normalized = normalizeBase64(cursor);
            byte[] decoded = Base64.getUrlDecoder().decode(normalized);
            String raw = new String(decoded, StandardCharsets.UTF_8);
            String[] parts = raw.split("\\|");
            if (parts.length != 3) {
                throw new BadRequestException("Invalid search cursor");
            }
            return new SearchCursorKey(
                    new BigDecimal(parts[0]),
                    Instant.parse(parts[1]),
                    UUID.fromString(parts[2])
            );
        } catch (IllegalArgumentException ex) {
            throw new BadRequestException("Invalid search cursor");
        }
    }

    private String normalizeBase64(String value) {
        int remainder = value.length() % 4;
        if (remainder == 0) {
            return value;
        }
        return value + "=".repeat(4 - remainder);
    }

    private record SearchCursorKey(BigDecimal score, Instant createdAt, UUID id) {
    }
}
