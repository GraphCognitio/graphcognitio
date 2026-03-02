package io.github.eduardopaim.graphcognitio.posts.service;

import io.github.eduardopaim.graphcognitio.posts.dto.ContextCluster;
import io.github.eduardopaim.graphcognitio.posts.dto.ContextFeedResponse;
import io.github.eduardopaim.graphcognitio.posts.dto.PostContextSummary;
import io.github.eduardopaim.graphcognitio.repository.PostFeedProjection;
import io.github.eduardopaim.graphcognitio.repository.PostRepository;
import io.github.eduardopaim.graphcognitio.repository.UserRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class FeedContextService {

    private static final int DEFAULT_FETCH_MULTIPLIER = 5;
    private static final int MAX_CLUSTER_HIGHLIGHTS = 3;

    private final PostRepository postRepository;
    private final UserRepository userRepository;
    private final TopicDiscoveryService topicDiscoveryService;

    public FeedContextService(PostRepository postRepository,
                              UserRepository userRepository,
                              TopicDiscoveryService topicDiscoveryService) {
        this.postRepository = postRepository;
        this.userRepository = userRepository;
        this.topicDiscoveryService = topicDiscoveryService;
    }

    public ContextFeedResponse getContextFeed(String cursor, int limit, Double minScore, String focusKeyword) {
        int fetchSize = Math.max(limit, 1) * DEFAULT_FETCH_MULTIPLIER;
        List<PostFeedProjection> posts = postRepository.findRelevantRootPosts(null, null, null, fetchSize);
        Map<UUID, String> authorNames = loadAuthorNames(posts);

        Map<String, ClusterAccumulator> clusters = new LinkedHashMap<>();
        for (PostFeedProjection projection : posts) {
            double score = projection.getFeedScore() == null ? 0.0 : projection.getFeedScore().doubleValue();
            if (minScore != null && score < minScore) {
                continue;
            }
            List<String> keywords = topicDiscoveryService.deriveTopicKeywords(projection.getContent(), 3);
            if (keywords.isEmpty()) {
                keywords = List.of("general");
            }
            boolean addedToCluster = false;
            for (String keyword : keywords) {
                if (focusKeyword != null && !keyword.equalsIgnoreCase(focusKeyword)) {
                    continue;
                }
                ClusterAccumulator builder = clusters.computeIfAbsent(keyword.toLowerCase(), k -> new ClusterAccumulator(keyword));
                builder.addProjection(projection, authorNames.getOrDefault(projection.getAuthorId(), "Unknown"), keywords, score);
                addedToCluster = true;
            }
            if (!addedToCluster && focusKeyword == null) {
                ClusterAccumulator fallback = clusters.computeIfAbsent("general", k -> new ClusterAccumulator("general"));
                fallback.addProjection(projection, authorNames.getOrDefault(projection.getAuthorId(), "Unknown"), keywords, score);
            }
        }

        List<ContextCluster> clusterList = clusters.values().stream()
                .filter(cluster -> !cluster.highlights.isEmpty())
                .sorted(Comparator.comparingDouble(ClusterAccumulator::score).reversed())
                .limit(limit)
                .map(ClusterAccumulator::toContextCluster)
                .collect(Collectors.toList());

        String nextCursor = null;
        if (clusterList.size() == limit) {
            ContextCluster last = clusterList.get(clusterList.size() - 1);
            nextCursor = Base64Encoder.encode(last.clusterId());
        }

        return new ContextFeedResponse(clusterList, nextCursor);
    }

    private Map<UUID, String> loadAuthorNames(List<PostFeedProjection> posts) {
        List<UUID> ids = posts.stream()
                .map(PostFeedProjection::getAuthorId)
                .distinct()
                .collect(Collectors.toList());
        return userRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(user -> user.getId(), user -> user.getName()));
    }

    private static final class ClusterAccumulator {
        private final String keyword;
        private final List<PostContextSummary> highlights = new LinkedList<>();
        private double score;

        private ClusterAccumulator(String keyword) {
            this.keyword = keyword;
        }

        void addProjection(PostFeedProjection projection, String authorName, List<String> keywords, double semanticScore) {
            PostContextSummary summary = new PostContextSummary(
                    projection.getId(),
                    projection.getRootId(),
                    authorName,
                    projection.getCreatedAt(),
                    projection.getLikeCount(),
                    projection.getReplyCount(),
                    keywords,
                    semanticScore
            );
            highlights.add(summary);
            highlights.sort(Comparator.comparingDouble(PostContextSummary::semanticScore).reversed());
            if (highlights.size() > MAX_CLUSTER_HIGHLIGHTS) {
                highlights.remove(highlights.size() - 1);
            }
            score = Math.max(score, semanticScore);
        }

        double score() {
            return score;
        }

        ContextCluster toContextCluster() {
            return new ContextCluster(
                    keyword,
                    List.copyOf(highlights.stream()
                            .flatMap(summary -> summary.topicKeywords().stream())
                            .distinct()
                            .collect(Collectors.toList())),
                    score,
                    List.copyOf(highlights)
            );
        }
    }

    private static final class Base64Encoder {
        private static String encode(String input) {
            return java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(input.getBytes());
        }
    }
}
