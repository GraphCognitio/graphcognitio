package io.github.eduardopaim.graphcognitio.posts.service;

import io.github.eduardopaim.graphcognitio.embeddings.EmbeddingVector;
import io.github.eduardopaim.graphcognitio.entity.UserRole;
import io.github.eduardopaim.graphcognitio.exception.BadRequestException;
import io.github.eduardopaim.graphcognitio.exception.ResourceNotFoundException;
import io.github.eduardopaim.graphcognitio.posts.dto.FeedResponse;
import io.github.eduardopaim.graphcognitio.posts.dto.PostResponse;
import io.github.eduardopaim.graphcognitio.repository.JdbcPostSearchProjection;
import io.github.eduardopaim.graphcognitio.repository.PostEmbeddingJdbcRepository;
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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class SearchService {

    private static final int MIN_VECTOR_CANDIDATES = 60;
    private static final int MAX_VECTOR_CANDIDATES = 480;

    private final PostRepository postRepository;
    private final PostEmbeddingJdbcRepository postEmbeddingJdbcRepository;
    private final UserRepository userRepository;
    private final PostLikeRepository postLikeRepository;
    private final TopicDiscoveryService topicDiscoveryService;
    private final PostEmbeddingService postEmbeddingService;

    public SearchService(PostRepository postRepository,
            PostEmbeddingJdbcRepository postEmbeddingJdbcRepository,
            UserRepository userRepository,
            PostLikeRepository postLikeRepository,
            TopicDiscoveryService topicDiscoveryService,
            PostEmbeddingService postEmbeddingService) {
        this.postRepository = postRepository;
        this.postEmbeddingJdbcRepository = postEmbeddingJdbcRepository;
        this.userRepository = userRepository;
        this.postLikeRepository = postLikeRepository;
        this.topicDiscoveryService = topicDiscoveryService;
        this.postEmbeddingService = postEmbeddingService;
    }

    @Transactional(readOnly = true)
    public FeedResponse search(String query, String cursor, int limit, UUID viewerId) {
        String normalizedQuery = normalizeQuery(query);
        String semanticQuery = topicDiscoveryService.buildSemanticSearchQuery(normalizedQuery);
        SearchCursorKey cursorKey = decodeCursor(cursor);

        List<PostSearchProjection> page = searchByEmbedding(normalizedQuery, semanticQuery, cursorKey, limit + 1);
        if (page.isEmpty()) {
            page = postRepository.searchPosts(
                    semanticQuery,
                    cursorKey == null ? null : cursorKey.score(),
                    cursorKey == null ? null : cursorKey.createdAt(),
                    cursorKey == null ? null : cursorKey.id(),
                    limit + 1);
        }

        boolean hasMore = page.size() > limit;
        List<PostSearchProjection> currentItems = hasMore ? new ArrayList<>(page.subList(0, limit)) : page;
        Map<UUID, AuthorDetails> authorDetails = loadAuthorDetails(currentItems);
        Set<UUID> likedPostIds = loadLikedPostIds(viewerId, currentItems);

        List<PostResponse> items = currentItems.stream()
                .map(post -> new PostResponse(
                        post.getId(),
                        post.getAuthorId(),
                        authorDetails.get(post.getAuthorId()).name(),
                        authorDetails.get(post.getAuthorId()).username(),
                        authorDetails.get(post.getAuthorId()).role(),
                        post.getContent(),
                        post.getCreatedAt(),
                        post.getUpdatedAt(),
                        post.getParentId(),
                        post.getRootId(),
                        post.getReplyCount(),
                        post.getLikeCount(),
                        likedPostIds.contains(post.getId()),
                        topicDiscoveryService.deriveTopicKeywords(post.getContent()),
                        post.getSearchScore().doubleValue(),
                        buildSearchSignals(post, normalizedQuery)))
                .toList();

        String nextCursor = null;
        if (hasMore && !currentItems.isEmpty()) {
            PostSearchProjection lastPost = currentItems.get(currentItems.size() - 1);
            nextCursor = encodeCursor(lastPost.getSearchScore(), lastPost.getCreatedAt(), lastPost.getId());
        }

        return new FeedResponse(items, nextCursor);
    }

    private List<PostSearchProjection> searchByEmbedding(String normalizedQuery,
            String semanticQuery,
            SearchCursorKey cursorKey,
            int pageSize) {
        if (!postEmbeddingService.isEnabled()) {
            return List.of();
        }

        EmbeddingVector queryEmbedding = postEmbeddingService
                .embedContent(buildSearchDocument(normalizedQuery, semanticQuery));
        int candidateLimit = Math.max(MIN_VECTOR_CANDIDATES, pageSize * 6);
        List<PostSearchProjection> filtered = List.of();

        while (candidateLimit <= MAX_VECTOR_CANDIDATES) {
            List<PostSearchProjection> candidates = postEmbeddingJdbcRepository.searchPostsByEmbedding(queryEmbedding,
                    candidateLimit);
            if (candidates.isEmpty()) {
                return List.of();
            }

            filtered = rerankVectorCandidates(candidates, normalizedQuery, semanticQuery, cursorKey);
            if (filtered.size() >= pageSize || candidates.size() < candidateLimit
                    || candidateLimit == MAX_VECTOR_CANDIDATES) {
                break;
            }
            candidateLimit = Math.min(candidateLimit * 2, MAX_VECTOR_CANDIDATES);
        }

        if (filtered.isEmpty()) {
            return List.of();
        }

        return filtered.subList(0, Math.min(filtered.size(), pageSize));
    }

    private List<PostSearchProjection> rerankVectorCandidates(List<PostSearchProjection> candidates,
            String normalizedQuery,
            String semanticQuery,
            SearchCursorKey cursorKey) {
        Set<String> queryKeywords = new LinkedHashSet<>(topicDiscoveryService.deriveTopicKeywords(normalizedQuery, 6));
        Set<String> semanticTerms = new LinkedHashSet<>(topicDiscoveryService.deriveTopicKeywords(semanticQuery, 6));
        String normalizedPhrase = normalizedQuery.toLowerCase(Locale.ROOT);

        return candidates.stream()
                .map(candidate -> rerankVectorCandidate(candidate, queryKeywords, semanticTerms, normalizedPhrase))
                .filter(candidate -> isAfterCursor(candidate, cursorKey))
                .sorted((left, right) -> {
                    int scoreComparison = right.getSearchScore().compareTo(left.getSearchScore());
                    if (scoreComparison != 0) {
                        return scoreComparison;
                    }

                    int createdAtComparison = right.getCreatedAt().compareTo(left.getCreatedAt());
                    if (createdAtComparison != 0) {
                        return createdAtComparison;
                    }
                    return right.getId().compareTo(left.getId());
                })
                .toList();
    }

    private PostSearchProjection rerankVectorCandidate(PostSearchProjection candidate,
            Set<String> queryKeywords,
            Set<String> semanticTerms,
            String normalizedPhrase) {
        List<String> candidateKeywords = topicDiscoveryService.deriveTopicKeywords(candidate.getContent(), 6);
        Set<String> candidateKeywordSet = new LinkedHashSet<>(candidateKeywords);

        long overlap = queryKeywords.stream()
                .filter(candidateKeywordSet::contains)
                .count();
        long semanticOverlap = semanticTerms.stream()
                .filter(candidateKeywordSet::contains)
                .count();

        String candidateContent = candidate.getContent() == null ? "" : candidate.getContent().toLowerCase(Locale.ROOT);
        double phraseBoost = !normalizedPhrase.isBlank() && candidateContent.contains(normalizedPhrase) ? 0.18 : 0.0;
        double engagementBoost = (Math.min(candidate.getLikeCount(), 20) * 0.012)
                + (Math.min(candidate.getReplyCount(), 20) * 0.009);
        double freshnessBoost = freshnessBoost(candidate.getCreatedAt());
        double score = candidate.getSearchScore().doubleValue()
                + (overlap * 0.08)
                + (semanticOverlap * 0.035)
                + phraseBoost
                + engagementBoost
                + freshnessBoost;

        return new JdbcPostSearchProjection(
                candidate.getId(),
                candidate.getAuthorId(),
                candidate.getContent(),
                candidate.getCreatedAt(),
                candidate.getUpdatedAt(),
                candidate.getParentId(),
                candidate.getRootId(),
                candidate.getReplyCount(),
                candidate.getLikeCount(),
                roundScore(score));
    }

    private boolean isAfterCursor(PostSearchProjection candidate, SearchCursorKey cursorKey) {
        if (cursorKey == null) {
            return true;
        }

        int scoreComparison = candidate.getSearchScore().compareTo(cursorKey.score());
        if (scoreComparison < 0) {
            return true;
        }
        if (scoreComparison > 0) {
            return false;
        }

        int createdAtComparison = candidate.getCreatedAt().compareTo(cursorKey.createdAt());
        if (createdAtComparison < 0) {
            return true;
        }
        if (createdAtComparison > 0) {
            return false;
        }

        return candidate.getId().compareTo(cursorKey.id()) < 0;
    }

    private double freshnessBoost(Instant createdAt) {
        Instant now = Instant.now();
        if (createdAt == null) {
            return 0.0;
        }
        if (createdAt.isAfter(now.minusSeconds(6 * 60 * 60))) {
            return 0.08;
        }
        if (createdAt.isAfter(now.minusSeconds(24 * 60 * 60))) {
            return 0.045;
        }
        if (createdAt.isAfter(now.minusSeconds(7L * 24 * 60 * 60))) {
            return 0.015;
        }
        return 0.0;
    }

    private BigDecimal roundScore(double score) {
        return BigDecimal.valueOf(score).setScale(8, java.math.RoundingMode.HALF_UP);
    }

    private String buildSearchDocument(String normalizedQuery, String semanticQuery) {
        if (!StringUtils.hasText(semanticQuery) || semanticQuery.equalsIgnoreCase(normalizedQuery)) {
            return normalizedQuery;
        }
        return normalizedQuery + "\n" + semanticQuery;
    }

    private List<String> buildSearchSignals(PostSearchProjection post, String normalizedQuery) {
        List<String> signals = new ArrayList<>();
        String content = post.getContent() == null ? "" : post.getContent().toLowerCase(Locale.ROOT);
        String query = normalizedQuery.toLowerCase(Locale.ROOT);
        if (content.contains(query)) {
            signals.add("Exact phrase");
        } else {
            signals.add("Semantic match");
        }
        if (post.getLikeCount() >= 2) {
            signals.add("Liked");
        }
        if (post.getReplyCount() >= 1) {
            signals.add("Thread activity");
        }
        return signals.stream().distinct().limit(3).toList();
    }

    private String normalizeQuery(String query) {
        if (!StringUtils.hasText(query)) {
            throw new BadRequestException("Search query cannot be blank");
        }
        return query.trim();
    }

    private Map<UUID, AuthorDetails> loadAuthorDetails(List<PostSearchProjection> posts) {
        Set<UUID> authorIds = posts.stream()
                .map(PostSearchProjection::getAuthorId)
                .collect(Collectors.toSet());

        if (authorIds.isEmpty()) {
            return Map.of();
        }

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
                    UUID.fromString(parts[2]));
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

    private record AuthorDetails(String name, String username, UserRole role) {
    }
}
