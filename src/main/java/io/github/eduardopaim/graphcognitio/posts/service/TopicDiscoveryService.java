package io.github.eduardopaim.graphcognitio.posts.service;

import io.github.eduardopaim.graphcognitio.embeddings.EmbeddingVector;
import io.github.eduardopaim.graphcognitio.entity.PostEntity;
import io.github.eduardopaim.graphcognitio.repository.PostEmbeddingJdbcRepository;
import io.github.eduardopaim.graphcognitio.repository.PostRepository;
import io.github.eduardopaim.graphcognitio.repository.PostSearchProjection;
import java.text.Normalizer;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TopicDiscoveryService {

    private static final int MAX_CONTEXT_POSTS = 8;
    private static final int MAX_QUERY_TERMS = 3;
    private static final int MAX_CANDIDATES = 24;
    private static final int DEFAULT_KEYWORD_LIMIT = 4;
    private static final Pattern DIACRITICS = Pattern.compile("\\p{M}+");
    private static final Pattern NON_ALPHANUMERIC = Pattern.compile("[^a-z0-9]+");
    private static final Set<String> STOP_WORDS = Set.of(
            "the", "and", "for", "with", "from", "that", "this", "into", "your", "have", "will",
            "about", "just", "some", "more", "than", "then", "when", "where", "while", "what",
            "como", "para", "com", "uma", "mais", "nao", "não", "que", "dos", "das", "por",
            "entre", "sobre", "esse", "essa", "isso", "isto", "aqui", "pois", "cada", "tambem",
            "também", "ainda", "muito", "pelo", "pela", "seus", "suas"
    );
    private static final Map<String, String> TOPIC_ALIASES = Map.ofEntries(
            Map.entry("graphs", "graph"),
            Map.entry("grafo", "graph"),
            Map.entry("grafos", "graph"),
            Map.entry("network", "graph"),
            Map.entry("networks", "graph"),
            Map.entry("mesh", "graph"),
            Map.entry("semantic", "semantic"),
            Map.entry("semantics", "semantic"),
            Map.entry("semantica", "semantic"),
            Map.entry("semanticas", "semantic"),
            Map.entry("semantico", "semantic"),
            Map.entry("semanticos", "semantic"),
            Map.entry("contextual", "context"),
            Map.entry("contexts", "context"),
            Map.entry("contexto", "context"),
            Map.entry("contextos", "context"),
            Map.entry("thread", "conversation"),
            Map.entry("threads", "conversation"),
            Map.entry("reply", "conversation"),
            Map.entry("replies", "conversation"),
            Map.entry("reposta", "conversation"),
            Map.entry("resposta", "conversation"),
            Map.entry("respostas", "conversation"),
            Map.entry("conversation", "conversation"),
            Map.entry("conversations", "conversation"),
            Map.entry("discuss", "conversation"),
            Map.entry("discussion", "conversation"),
            Map.entry("discussions", "conversation"),
            Map.entry("feed", "feed"),
            Map.entry("feeds", "feed"),
            Map.entry("ranking", "ranking"),
            Map.entry("ranked", "ranking"),
            Map.entry("rankings", "ranking"),
            Map.entry("recommendation", "ranking"),
            Map.entry("recommendations", "ranking"),
            Map.entry("relevance", "ranking"),
            Map.entry("relevant", "ranking"),
            Map.entry("topic", "topic"),
            Map.entry("topics", "topic"),
            Map.entry("theme", "topic"),
            Map.entry("themes", "topic"),
            Map.entry("tema", "topic"),
            Map.entry("temas", "topic"),
            Map.entry("cluster", "cluster"),
            Map.entry("clusters", "cluster"),
            Map.entry("clustering", "cluster"),
            Map.entry("map", "mapping"),
            Map.entry("maps", "mapping"),
            Map.entry("mapping", "mapping"),
            Map.entry("mappings", "mapping"),
            Map.entry("search", "search"),
            Map.entry("searches", "search"),
            Map.entry("busca", "search"),
            Map.entry("buscas", "search"),
            Map.entry("discovery", "discovery"),
            Map.entry("discover", "discovery"),
            Map.entry("discoverability", "discovery"),
            Map.entry("explore", "discovery"),
            Map.entry("api", "api"),
            Map.entry("apis", "api"),
            Map.entry("backend", "api"),
            Map.entry("endpoint", "api"),
            Map.entry("endpoints", "api"),
            Map.entry("frontend", "ui"),
            Map.entry("interface", "ui"),
            Map.entry("interfaces", "ui"),
            Map.entry("canvas", "ui"),
            Map.entry("visual", "visual"),
            Map.entry("visualization", "visual"),
            Map.entry("visualizations", "visual"),
            Map.entry("visualizacao", "visual"),
            Map.entry("visualizacoes", "visual"),
            Map.entry("jwt", "auth"),
            Map.entry("token", "auth"),
            Map.entry("tokens", "auth"),
            Map.entry("auth", "auth"),
            Map.entry("authentication", "auth"),
            Map.entry("security", "auth"),
            Map.entry("moderation", "moderation"),
            Map.entry("moderate", "moderation"),
            Map.entry("admin", "moderation"),
            Map.entry("admins", "moderation"),
            Map.entry("role", "moderation"),
            Map.entry("roles", "moderation")
    );

    private final PostRepository postRepository;
    private final PostEmbeddingJdbcRepository postEmbeddingJdbcRepository;
    private final PostEmbeddingService postEmbeddingService;

    public TopicDiscoveryService(PostRepository postRepository,
                                 PostEmbeddingJdbcRepository postEmbeddingJdbcRepository,
                                 PostEmbeddingService postEmbeddingService) {
        this.postRepository = postRepository;
        this.postEmbeddingJdbcRepository = postEmbeddingJdbcRepository;
        this.postEmbeddingService = postEmbeddingService;
    }

    public List<String> deriveTopicKeywords(String content) {
        return deriveTopicKeywords(content, DEFAULT_KEYWORD_LIMIT);
    }

    public List<String> deriveTopicKeywords(String content, int limit) {
        if (limit <= 0) {
            return List.of();
        }
        return rankKeywords(toWeightedTerms(extractTerms(content), 1.0), limit);
    }

    public String buildSemanticSearchQuery(String query) {
        List<String> keywords = deriveTopicKeywords(query, MAX_QUERY_TERMS);
        if (!keywords.isEmpty()) {
            return String.join(" ", keywords);
        }
        return query == null ? "" : query.trim();
    }

    @Transactional(readOnly = true)
    public List<TopicMatchResult> findRelatedRootPosts(PostEntity seedPost, int limit) {
        if (seedPost == null || limit <= 0) {
            return List.of();
        }

        List<PostEntity> threadPosts = postRepository.findByRootIdOrderByCreatedAtAscIdAsc(seedPost.getRootId());
        if (threadPosts.isEmpty()) {
            threadPosts = List.of(seedPost);
        }

        Map<String, Double> contextWeights = buildContextWeights(threadPosts, seedPost);
        String searchQuery = buildSearchQuery(contextWeights, seedPost.getContent());
        if (searchQuery.isBlank()) {
            return List.of();
        }

        int candidateLimit = Math.min(Math.max(limit * 4, limit), MAX_CANDIDATES);
        List<PostSearchProjection> candidates = loadCandidates(seedPost, searchQuery, buildContextDocument(threadPosts, seedPost), candidateLimit);
        if (candidates.isEmpty()) {
            return List.of();
        }

        Set<String> contextBigrams = buildContextBigrams(threadPosts, seedPost);
        return candidates.stream()
                .map(candidate -> toTopicCandidate(candidate, contextWeights, contextBigrams))
                .sorted(Comparator
                        .comparingDouble(TopicCandidateView::semanticScore).reversed()
                        .thenComparing((left, right) -> compareNullableDoubles(
                                left.projection().getSearchScore() == null ? null : left.projection().getSearchScore().doubleValue(),
                                right.projection().getSearchScore() == null ? null : right.projection().getSearchScore().doubleValue()
                        ))
                        .thenComparing((left, right) -> right.projection().getCreatedAt().compareTo(left.projection().getCreatedAt()))
                        .thenComparing((left, right) -> right.projection().getId().compareTo(left.projection().getId())))
                .limit(limit)
                .map(candidate -> new TopicMatchResult(
                        candidate.projection(),
                        roundScore(candidate.semanticScore()),
                        candidate.matchedTerms(),
                        candidate.topicReason()
                ))
                .toList();
    }

    private TopicCandidateView toTopicCandidate(PostSearchProjection candidate,
                                                Map<String, Double> contextWeights,
                                                Set<String> contextBigrams) {
        List<String> candidateTerms = extractTerms(candidate.getContent());
        Set<String> candidateTermSet = new LinkedHashSet<>(candidateTerms);
        List<String> matchedTerms = contextWeights.entrySet().stream()
                .filter(entry -> candidateTermSet.contains(entry.getKey()))
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .map(Map.Entry::getKey)
                .distinct()
                .limit(4)
                .toList();

        return new TopicCandidateView(
                candidate,
                scoreCandidate(candidate, contextWeights, contextBigrams, candidateTermSet),
                matchedTerms,
                buildTopicReason(matchedTerms)
        );
    }

    private List<PostSearchProjection> loadCandidates(PostEntity seedPost,
                                                      String searchQuery,
                                                      String contextDocument,
                                                      int candidateLimit) {
        if (postEmbeddingService.isEnabled()) {
            EmbeddingVector contextEmbedding = postEmbeddingService.embedContent(contextDocument);
            List<PostSearchProjection> vectorMatches = postEmbeddingJdbcRepository.findRelatedRootPostsByEmbedding(
                    seedPost.getId(),
                    seedPost.getRootId(),
                    contextEmbedding,
                    candidateLimit
            );
            if (!vectorMatches.isEmpty()) {
                return vectorMatches;
            }
        }

        return postRepository.findRelatedRootPosts(
                seedPost.getId(),
                seedPost.getRootId(),
                searchQuery,
                candidateLimit
        );
    }

    private Map<String, Double> buildContextWeights(List<PostEntity> threadPosts, PostEntity seedPost) {
        Map<String, Double> weights = new HashMap<>();
        PostEntity rootPost = threadPosts.stream()
                .filter(post -> post.getParentId() == null)
                .findFirst()
                .orElse(seedPost);

        addWeightedTerms(weights, rootPost.getContent(), 1.2);
        if (!rootPost.getId().equals(seedPost.getId())) {
            addWeightedTerms(weights, seedPost.getContent(), 1.45);
        }

        int remainingPosts = Math.max(MAX_CONTEXT_POSTS - 2, 0);
        for (PostEntity post : threadPosts) {
            if (remainingPosts == 0) {
                break;
            }
            if (post.getId().equals(rootPost.getId()) || post.getId().equals(seedPost.getId())) {
                continue;
            }
            addWeightedTerms(weights, post.getContent(), 0.55);
            remainingPosts--;
        }

        return weights;
    }

    private void addWeightedTerms(Map<String, Double> weights, String content, double factor) {
        for (String term : extractTerms(content)) {
            weights.merge(term, factor, Double::sum);
        }
    }

    private String buildSearchQuery(Map<String, Double> contextWeights, String fallbackContent) {
        List<String> weightedTerms = rankKeywords(contextWeights, MAX_QUERY_TERMS);
        if (!weightedTerms.isEmpty()) {
            return String.join(" ", weightedTerms);
        }

        List<String> fallbackTerms = deriveTopicKeywords(fallbackContent, MAX_QUERY_TERMS);
        if (!fallbackTerms.isEmpty()) {
            return String.join(" ", fallbackTerms);
        }

        return fallbackContent == null ? "" : fallbackContent.trim();
    }

    private String buildContextDocument(List<PostEntity> threadPosts, PostEntity seedPost) {
        StringBuilder builder = new StringBuilder();
        PostEntity rootPost = threadPosts.stream()
                .filter(post -> post.getParentId() == null)
                .findFirst()
                .orElse(seedPost);

        appendContextSegment(builder, rootPost.getContent());
        if (!rootPost.getId().equals(seedPost.getId())) {
            appendContextSegment(builder, seedPost.getContent());
        }

        int remainingPosts = Math.max(MAX_CONTEXT_POSTS - 2, 0);
        for (PostEntity post : threadPosts) {
            if (remainingPosts == 0) {
                break;
            }
            if (post.getId().equals(rootPost.getId()) || post.getId().equals(seedPost.getId())) {
                continue;
            }
            appendContextSegment(builder, post.getContent());
            remainingPosts--;
        }

        return builder.toString().trim();
    }

    private void appendContextSegment(StringBuilder builder, String content) {
        if (content == null || content.isBlank()) {
            return;
        }
        if (builder.length() > 0) {
            builder.append('\n');
        }
        builder.append(content.trim());
    }

    private Set<String> buildContextBigrams(List<PostEntity> threadPosts, PostEntity seedPost) {
        PostEntity rootPost = threadPosts.stream()
                .filter(post -> post.getParentId() == null)
                .findFirst()
                .orElse(seedPost);

        LinkedHashSet<String> orderedTerms = new LinkedHashSet<>();
        orderedTerms.addAll(extractTerms(rootPost.getContent()));
        if (!rootPost.getId().equals(seedPost.getId())) {
            orderedTerms.addAll(extractTerms(seedPost.getContent()));
        }

        int remainingPosts = 2;
        for (PostEntity post : threadPosts) {
            if (remainingPosts == 0) {
                break;
            }
            if (post.getId().equals(rootPost.getId()) || post.getId().equals(seedPost.getId())) {
                continue;
            }
            orderedTerms.addAll(extractTerms(post.getContent()));
            remainingPosts--;
        }
        return extractBigrams(new ArrayList<>(orderedTerms));
    }

    private double scoreCandidate(PostSearchProjection candidate,
                                  Map<String, Double> contextWeights,
                                  Set<String> contextBigrams,
                                  Set<String> candidateTerms) {
        double baseScore = candidate.getSearchScore() == null ? 0.0 : candidate.getSearchScore().doubleValue();
        Set<String> candidateBigrams = extractBigrams(new ArrayList<>(candidateTerms));
        double keywordSupport = weightedOverlap(candidateTerms, contextWeights);
        double phraseSupport = candidateBigrams.isEmpty()
                ? 0.0
                : candidateBigrams.stream().filter(contextBigrams::contains).count() / (double) candidateBigrams.size();
        double engagementBoost = Math.min(candidate.getLikeCount(), 12) * 0.01
                + Math.min(candidate.getReplyCount(), 10) * 0.014;
        double freshnessBoost = freshnessBoost(candidate.getCreatedAt());

        return (baseScore * 1.72)
                + (phraseSupport * 0.08)
                + (keywordSupport * 0.18)
                + engagementBoost
                + freshnessBoost;
    }

    private String buildTopicReason(List<String> matchedTerms) {
        if (matchedTerms.isEmpty()) {
            return "Embedding similarity with the current thread";
        }
        if (matchedTerms.size() == 1) {
            return "Embedding match around: " + matchedTerms.get(0);
        }
        return "Embedding match around: " + String.join(", ", matchedTerms.subList(0, Math.min(3, matchedTerms.size())));
    }

    private double weightedOverlap(Set<String> candidateTerms, Map<String, Double> contextWeights) {
        if (candidateTerms.isEmpty() || contextWeights.isEmpty()) {
            return 0.0;
        }

        double total = contextWeights.values().stream()
                .mapToDouble(Double::doubleValue)
                .sum();
        if (total <= 0) {
            return 0.0;
        }

        double matched = 0.0;
        for (String term : candidateTerms) {
            matched += contextWeights.getOrDefault(term, 0.0);
        }
        return matched / total;
    }

    private Set<String> extractBigrams(List<String> orderedTerms) {
        if (orderedTerms.size() < 2) {
            return Set.of();
        }

        Set<String> bigrams = new LinkedHashSet<>();
        for (int index = 0; index < orderedTerms.size() - 1; index++) {
            bigrams.add(orderedTerms.get(index) + ":" + orderedTerms.get(index + 1));
        }
        return bigrams;
    }

    private double freshnessBoost(Instant createdAt) {
        Duration age = Duration.between(createdAt, Instant.now());
        if (age.compareTo(Duration.ofDays(2)) <= 0) {
            return 0.12;
        }
        if (age.compareTo(Duration.ofDays(7)) <= 0) {
            return 0.05;
        }
        return 0.0;
    }

    private List<String> extractTerms(String content) {
        if (content == null || content.isBlank()) {
            return List.of();
        }

        String normalized = normalize(content);
        if (normalized.isBlank()) {
            return List.of();
        }

        List<String> terms = new ArrayList<>();
        for (String token : NON_ALPHANUMERIC.split(normalized)) {
            String canonicalToken = canonicalizeToken(token);
            if (!isUsefulToken(canonicalToken)) {
                continue;
            }
            terms.add(canonicalToken);
        }
        return terms;
    }

    private String normalize(String value) {
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFD);
        normalized = DIACRITICS.matcher(normalized).replaceAll("");
        return normalized.toLowerCase(Locale.ROOT).trim();
    }

    private boolean isUsefulToken(String token) {
        return token.length() >= 3
                && !STOP_WORDS.contains(token)
                && token.chars().anyMatch(character -> character >= 'a' && character <= 'z');
    }

    private String canonicalizeToken(String token) {
        if (token == null || token.isBlank()) {
            return "";
        }

        String normalizedToken = token.toLowerCase(Locale.ROOT);
        String alias = TOPIC_ALIASES.get(normalizedToken);
        if (alias != null) {
            return alias;
        }

        String collapsed = stripCommonSuffixes(normalizedToken);
        alias = TOPIC_ALIASES.get(collapsed);
        return alias != null ? alias : collapsed;
    }

    private String stripCommonSuffixes(String token) {
        String normalizedToken = token;

        if (normalizedToken.endsWith("ing") && normalizedToken.length() > 5) {
            normalizedToken = normalizedToken.substring(0, normalizedToken.length() - 3);
        } else if (normalizedToken.endsWith("ed") && normalizedToken.length() > 4) {
            normalizedToken = normalizedToken.substring(0, normalizedToken.length() - 2);
        }

        if (normalizedToken.endsWith("es") && normalizedToken.length() > 4) {
            normalizedToken = normalizedToken.substring(0, normalizedToken.length() - 2);
        } else if (normalizedToken.endsWith("s") && normalizedToken.length() > 4) {
            normalizedToken = normalizedToken.substring(0, normalizedToken.length() - 1);
        }

        if (normalizedToken.endsWith("mente") && normalizedToken.length() > 7) {
            normalizedToken = normalizedToken.substring(0, normalizedToken.length() - 5);
        }

        return normalizedToken;
    }

    private Map<String, Double> toWeightedTerms(List<String> terms, double factor) {
        Map<String, Double> weights = new HashMap<>();
        for (int index = 0; index < terms.size(); index++) {
            String term = terms.get(index);
            double positionBoost = Math.max(0.0, 0.24 - (index * 0.03));
            weights.merge(term, factor + positionBoost, Double::sum);
        }
        return weights;
    }

    private List<String> rankKeywords(Map<String, Double> weights, int limit) {
        if (weights.isEmpty() || limit <= 0) {
            return List.of();
        }

        return weights.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed()
                        .thenComparing(Map.Entry::getKey))
                .limit(limit)
                .map(Map.Entry::getKey)
                .toList();
    }

    private double roundScore(double value) {
        return Math.round(value * 1000.0) / 1000.0;
    }

    private static int compareNullableDoubles(Double left, Double right) {
        if (left == null && right == null) {
            return 0;
        }
        if (left == null) {
            return 1;
        }
        if (right == null) {
            return -1;
        }
        return Double.compare(right, left);
    }

    private record TopicCandidateView(
            PostSearchProjection projection,
            double semanticScore,
            List<String> matchedTerms,
            String topicReason
    ) {
    }
}
