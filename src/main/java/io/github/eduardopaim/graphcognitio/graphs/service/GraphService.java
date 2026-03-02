package io.github.eduardopaim.graphcognitio.graphs.service;

import io.github.eduardopaim.graphcognitio.config.FeatureFlagsProperties;
import io.github.eduardopaim.graphcognitio.entity.EdgeEntity;
import io.github.eduardopaim.graphcognitio.entity.PostEntity;
import io.github.eduardopaim.graphcognitio.entity.UserRole;
import io.github.eduardopaim.graphcognitio.exception.BadRequestException;
import io.github.eduardopaim.graphcognitio.exception.ResourceNotFoundException;
import io.github.eduardopaim.graphcognitio.graphs.GraphMode;
import io.github.eduardopaim.graphcognitio.graphs.dto.ConversationEdgeResponse;
import io.github.eduardopaim.graphcognitio.graphs.dto.ConversationGraphResponse;
import io.github.eduardopaim.graphcognitio.graphs.dto.ConversationNodeResponse;
import io.github.eduardopaim.graphcognitio.posts.service.TopicMatchResult;
import io.github.eduardopaim.graphcognitio.posts.service.TopicDiscoveryService;
import io.github.eduardopaim.graphcognitio.repository.EdgeRepository;
import io.github.eduardopaim.graphcognitio.repository.PostRepository;
import io.github.eduardopaim.graphcognitio.repository.UserRepository;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GraphService {

    private static final int CONTENT_PREVIEW_MAX = 140;
    private static final int MAX_TOPIC_MATCHES = 8;

    private final PostRepository postRepository;
    private final EdgeRepository edgeRepository;
    private final UserRepository userRepository;
    private final FeatureFlagsProperties featureFlagsProperties;
    private final TopicDiscoveryService topicDiscoveryService;

    public GraphService(PostRepository postRepository,
                        EdgeRepository edgeRepository,
                        UserRepository userRepository,
                        FeatureFlagsProperties featureFlagsProperties,
                        TopicDiscoveryService topicDiscoveryService) {
        this.postRepository = postRepository;
        this.edgeRepository = edgeRepository;
        this.userRepository = userRepository;
        this.featureFlagsProperties = featureFlagsProperties;
        this.topicDiscoveryService = topicDiscoveryService;
    }

    @Transactional(readOnly = true)
    @Cacheable(
            cacheNames = "graphCache",
            key = "T(io.github.eduardopaim.graphcognitio.cache.CacheKeys).graph(#rootId, #depth, #limit, #mode)"
    )
    public ConversationGraphResponse getConversationGraph(UUID rootId, int depth, int limit, GraphMode mode) {
        if (depth > 2 && !featureFlagsProperties.isGraphExpandEnabled()) {
            throw new BadRequestException("Graph expand feature is disabled");
        }

        PostEntity root = postRepository.findByIdAndParentIdIsNull(rootId)
                .orElseThrow(() -> new ResourceNotFoundException("Root post not found"));

        GraphCollection conversationGraph = mode == GraphMode.TOPIC
                ? new GraphCollection(List.of(root), List.of(), new HashSet<>(Set.of(root.getId())))
                : collectConversationGraph(root, depth, limit);

        List<PostEntity> collectedPosts = new ArrayList<>(conversationGraph.posts());
        List<GraphEdgeView> collectedEdges = new ArrayList<>(conversationGraph.edges());
        Set<UUID> visited = new HashSet<>(conversationGraph.visitedNodeIds());
        Map<UUID, TopicMatchResult> topicMatchByPostId = new HashMap<>();

        if (mode != GraphMode.CONVERSATION && collectedPosts.size() < limit) {
            int relatedLimit = Math.min(MAX_TOPIC_MATCHES, limit - collectedPosts.size());
            List<TopicMatchResult> relatedRoots = loadTopicMatches(root, relatedLimit);
            for (TopicMatchResult relatedMatch : relatedRoots) {
                PostEntity relatedRoot = toPostEntity(relatedMatch.projection());
                if (!visited.add(relatedRoot.getId())) {
                    continue;
                }
                collectedPosts.add(relatedRoot);
                topicMatchByPostId.put(relatedRoot.getId(), relatedMatch);
                collectedEdges.add(new GraphEdgeView(
                        buildDeterministicEdgeId(root.getId(), relatedRoot.getId()),
                        root.getId(),
                        relatedRoot.getId(),
                        "RELATED",
                        relatedRoot.getCreatedAt()
                ));
            }
        }

        Map<UUID, AuthorDetails> authorDetails = loadAuthorDetails(collectedPosts);
        List<ConversationNodeResponse> nodes = collectedPosts.stream()
                .map(post -> new ConversationNodeResponse(
                        post.getId(),
                        post.getAuthorId(),
                        authorDetails.get(post.getAuthorId()).name(),
                        authorDetails.get(post.getAuthorId()).role(),
                        buildContentPreview(post.getContent()),
                        post.getCreatedAt(),
                        post.getParentId(),
                        post.getRootId(),
                        post.getReplyCount(),
                        topicMatchByPostId.containsKey(post.getId()) ? topicMatchByPostId.get(post.getId()).topicScore() : null,
                        topicMatchByPostId.containsKey(post.getId()) ? topicMatchByPostId.get(post.getId()).matchedTerms() : null,
                        topicMatchByPostId.containsKey(post.getId()) ? topicMatchByPostId.get(post.getId()).topicReason() : null
                ))
                .toList();

        List<ConversationEdgeResponse> edges = collectedEdges.stream()
                .map(edge -> new ConversationEdgeResponse(
                        edge.id(),
                        edge.sourcePostId(),
                        edge.targetPostId(),
                        edge.type(),
                        edge.createdAt()
                ))
                .toList();

        return new ConversationGraphResponse(nodes, edges);
    }

    private GraphCollection collectConversationGraph(PostEntity root, int depth, int limit) {
        List<PostEntity> collectedPosts = new ArrayList<>();
        List<GraphEdgeView> collectedEdges = new ArrayList<>();
        Set<UUID> visited = new HashSet<>();

        collectedPosts.add(root);
        visited.add(root.getId());

        List<UUID> frontier = List.of(root.getId());
        int currentDepth = 0;

        while (currentDepth < depth && !frontier.isEmpty() && collectedPosts.size() < limit) {
            List<PostEntity> children = postRepository.findByParentIdInOrderByCreatedAtAscIdAsc(frontier);
            if (children.isEmpty()) {
                break;
            }

            List<PostEntity> acceptedChildren = new ArrayList<>();
            for (PostEntity child : children) {
                if (visited.contains(child.getId())) {
                    continue;
                }
                if (collectedPosts.size() >= limit) {
                    break;
                }
                visited.add(child.getId());
                collectedPosts.add(child);
                acceptedChildren.add(child);
            }

            if (acceptedChildren.isEmpty()) {
                break;
            }

            List<UUID> acceptedIds = acceptedChildren.stream()
                    .map(PostEntity::getId)
                    .toList();

            List<EdgeEntity> layerEdges = edgeRepository.findBySourcePostIdInOrderByCreatedAtAscIdAsc(acceptedIds);
            layerEdges.stream()
                    .filter(edge -> visited.contains(edge.getSourcePostId()) && visited.contains(edge.getTargetPostId()))
                    .map(edge -> new GraphEdgeView(
                            edge.getId(),
                            edge.getSourcePostId(),
                            edge.getTargetPostId(),
                            edge.getType().name(),
                            edge.getCreatedAt()
                    ))
                    .forEach(collectedEdges::add);

            frontier = acceptedIds;
            currentDepth++;
        }

        return new GraphCollection(collectedPosts, collectedEdges, visited);
    }

    private List<TopicMatchResult> loadTopicMatches(PostEntity root, int limit) {
        if (limit <= 0) {
            return List.of();
        }

        return topicDiscoveryService.findRelatedRootPosts(root, limit);
    }

    private PostEntity toPostEntity(io.github.eduardopaim.graphcognitio.repository.PostSearchProjection relatedRoot) {
        PostEntity post = new PostEntity();
        post.setId(relatedRoot.getId());
        post.setAuthorId(relatedRoot.getAuthorId());
        post.setContent(relatedRoot.getContent());
        post.setCreatedAt(relatedRoot.getCreatedAt());
        post.setParentId(relatedRoot.getParentId());
        post.setRootId(relatedRoot.getRootId());
        post.setReplyCount(relatedRoot.getReplyCount());
        post.setLikeCount(relatedRoot.getLikeCount());
        return post;
    }

    private Map<UUID, AuthorDetails> loadAuthorDetails(List<PostEntity> posts) {
        Set<UUID> authorIds = posts.stream()
                .map(PostEntity::getAuthorId)
                .collect(Collectors.toSet());

        Map<UUID, AuthorDetails> authorDetails = new HashMap<>();
        userRepository.findAllById(authorIds)
                .forEach(author -> authorDetails.put(author.getId(), new AuthorDetails(author.getName(), author.getRole())));

        for (UUID authorId : authorIds) {
            if (!authorDetails.containsKey(authorId)) {
                throw new ResourceNotFoundException("Author not found");
            }
        }
        return authorDetails;
    }

    private String buildContentPreview(String content) {
        if (content == null || content.isEmpty()) {
            return "";
        }
        if (content.length() <= CONTENT_PREVIEW_MAX) {
            return content;
        }
        return content.substring(0, CONTENT_PREVIEW_MAX);
    }

    private UUID buildDeterministicEdgeId(UUID rootId, UUID relatedRootId) {
        String seed = rootId + ":related:" + relatedRootId;
        return UUID.nameUUIDFromBytes(seed.getBytes(StandardCharsets.UTF_8));
    }

    private record GraphCollection(
            List<PostEntity> posts,
            List<GraphEdgeView> edges,
            Set<UUID> visitedNodeIds
    ) {
    }

    private record GraphEdgeView(
            UUID id,
            UUID sourcePostId,
            UUID targetPostId,
            String type,
            java.time.Instant createdAt
    ) {
    }

    private record AuthorDetails(String name, UserRole role) {
    }
}
