package io.github.eduardopaim.graphcognitio.graphs.service;

import io.github.eduardopaim.graphcognitio.entity.EdgeEntity;
import io.github.eduardopaim.graphcognitio.entity.PostEntity;
import io.github.eduardopaim.graphcognitio.exception.ResourceNotFoundException;
import io.github.eduardopaim.graphcognitio.graphs.dto.ConversationEdgeResponse;
import io.github.eduardopaim.graphcognitio.graphs.dto.ConversationGraphResponse;
import io.github.eduardopaim.graphcognitio.graphs.dto.ConversationNodeResponse;
import io.github.eduardopaim.graphcognitio.repository.EdgeRepository;
import io.github.eduardopaim.graphcognitio.repository.PostRepository;
import io.github.eduardopaim.graphcognitio.repository.UserRepository;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GraphService {

    private static final int CONTENT_PREVIEW_MAX = 140;

    private final PostRepository postRepository;
    private final EdgeRepository edgeRepository;
    private final UserRepository userRepository;

    public GraphService(PostRepository postRepository,
                        EdgeRepository edgeRepository,
                        UserRepository userRepository) {
        this.postRepository = postRepository;
        this.edgeRepository = edgeRepository;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public ConversationGraphResponse getConversationGraph(UUID rootId, int depth, int limit) {
        PostEntity root = postRepository.findByIdAndParentIdIsNull(rootId)
                .orElseThrow(() -> new ResourceNotFoundException("Root post not found"));

        List<PostEntity> collectedPosts = new ArrayList<>();
        List<EdgeEntity> collectedEdges = new ArrayList<>();
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
            collectedEdges.addAll(layerEdges);

            frontier = acceptedIds;
            currentDepth++;
        }

        Map<UUID, String> authorNames = loadAuthorNames(collectedPosts);
        List<ConversationNodeResponse> nodes = collectedPosts.stream()
                .map(post -> new ConversationNodeResponse(
                        post.getId(),
                        post.getAuthorId(),
                        authorNames.get(post.getAuthorId()),
                        buildContentPreview(post.getContent()),
                        post.getCreatedAt(),
                        post.getParentId(),
                        post.getRootId(),
                        post.getReplyCount()
                ))
                .toList();

        List<ConversationEdgeResponse> edges = collectedEdges.stream()
                .filter(edge -> visited.contains(edge.getSourcePostId()) && visited.contains(edge.getTargetPostId()))
                .map(edge -> new ConversationEdgeResponse(
                        edge.getId(),
                        edge.getSourcePostId(),
                        edge.getTargetPostId(),
                        edge.getType().name(),
                        edge.getCreatedAt()
                ))
                .toList();

        return new ConversationGraphResponse(nodes, edges);
    }

    private Map<UUID, String> loadAuthorNames(List<PostEntity> posts) {
        Set<UUID> authorIds = posts.stream()
                .map(PostEntity::getAuthorId)
                .collect(Collectors.toSet());

        Map<UUID, String> authorNames = new HashMap<>();
        userRepository.findAllById(authorIds).forEach(author -> authorNames.put(author.getId(), author.getName()));

        for (UUID authorId : authorIds) {
            if (!authorNames.containsKey(authorId)) {
                throw new ResourceNotFoundException("Author not found");
            }
        }
        return authorNames;
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
}
