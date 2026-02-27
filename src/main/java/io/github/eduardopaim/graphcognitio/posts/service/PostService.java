package io.github.eduardopaim.graphcognitio.posts.service;

import io.github.eduardopaim.graphcognitio.audit.service.AuditLogService;
import io.github.eduardopaim.graphcognitio.entity.EdgeEntity;
import io.github.eduardopaim.graphcognitio.entity.EdgeType;
import io.github.eduardopaim.graphcognitio.entity.PostEntity;
import io.github.eduardopaim.graphcognitio.entity.PostLikeEntity;
import io.github.eduardopaim.graphcognitio.entity.UserEntity;
import io.github.eduardopaim.graphcognitio.entity.UserRole;
import io.github.eduardopaim.graphcognitio.exception.BadRequestException;
import io.github.eduardopaim.graphcognitio.exception.ConflictException;
import io.github.eduardopaim.graphcognitio.exception.ForbiddenException;
import io.github.eduardopaim.graphcognitio.exception.ResourceNotFoundException;
import io.github.eduardopaim.graphcognitio.exception.UnauthorizedException;
import io.github.eduardopaim.graphcognitio.posts.dto.CreatePostRequest;
import io.github.eduardopaim.graphcognitio.posts.dto.FeedResponse;
import io.github.eduardopaim.graphcognitio.posts.dto.PostResponse;
import io.github.eduardopaim.graphcognitio.posts.dto.UpdatePostRequest;
import io.github.eduardopaim.graphcognitio.posts.mapper.PostMapper;
import io.github.eduardopaim.graphcognitio.repository.EdgeRepository;
import io.github.eduardopaim.graphcognitio.repository.PostLikeRepository;
import io.github.eduardopaim.graphcognitio.repository.PostRepository;
import io.github.eduardopaim.graphcognitio.repository.UserRepository;
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
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class PostService {

    private final PostRepository postRepository;
    private final UserRepository userRepository;
    private final EdgeRepository edgeRepository;
    private final PostLikeRepository postLikeRepository;
    private final PostMapper postMapper;
    private final AuditLogService auditLogService;

    public PostService(PostRepository postRepository,
                       UserRepository userRepository,
                       EdgeRepository edgeRepository,
                       PostLikeRepository postLikeRepository,
                       PostMapper postMapper,
                       AuditLogService auditLogService) {
        this.postRepository = postRepository;
        this.userRepository = userRepository;
        this.edgeRepository = edgeRepository;
        this.postLikeRepository = postLikeRepository;
        this.postMapper = postMapper;
        this.auditLogService = auditLogService;
    }

    @Transactional
    @CacheEvict(cacheNames = "feedCache", allEntries = true)
    public PostResponse createRootPost(UUID authorId, CreatePostRequest request) {
        UserEntity author = loadRequiredUser(authorId);

        UUID postId = UUID.randomUUID();
        PostEntity post = new PostEntity();
        post.setId(postId);
        post.setAuthorId(authorId);
        post.setContent(request.content().trim());
        post.setCreatedAt(Instant.now());
        post.setParentId(null);
        post.setRootId(postId);
        post.setReplyCount(0);
        post.setLikeCount(0);

        PostEntity saved = postRepository.save(post);
        auditLogService.log(
                author.getId(),
                "POST_CREATED",
                "POST",
                saved.getId(),
                Map.of("isRoot", true)
        );
        return postMapper.toResponse(saved, author.getName(), false);
    }

    @Transactional
    @Caching(evict = {
            @CacheEvict(cacheNames = "feedCache", allEntries = true),
            @CacheEvict(cacheNames = "graphCache", allEntries = true)
    })
    public PostResponse createReply(UUID authorId, UUID parentPostId, CreatePostRequest request) {
        UserEntity author = loadRequiredUser(authorId);

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
        replyPost.setLikeCount(0);

        PostEntity savedReply = postRepository.save(replyPost);

        parentPost.setReplyCount(parentPost.getReplyCount() + 1);
        postRepository.save(parentPost);

        EdgeEntity edge = new EdgeEntity();
        edge.setSourcePostId(savedReply.getId());
        edge.setTargetPostId(parentPost.getId());
        edge.setType(EdgeType.REPLY);
        edge.setCreatedAt(Instant.now());
        edgeRepository.save(edge);

        auditLogService.log(
                author.getId(),
                "POST_REPLY_CREATED",
                "POST",
                savedReply.getId(),
                Map.of(
                        "parentPostId", parentPost.getId().toString(),
                        "rootId", savedReply.getRootId().toString()
                )
        );

        return postMapper.toResponse(savedReply, author.getName(), false);
    }

    @Transactional(readOnly = true)
    public PostResponse getPostById(UUID postId, UUID viewerId) {
        PostEntity post = postRepository.findById(postId)
                .orElseThrow(() -> new ResourceNotFoundException("Post not found"));

        String authorName = userRepository.findById(post.getAuthorId())
                .map(UserEntity::getName)
                .orElseThrow(() -> new ResourceNotFoundException("Author not found"));

        boolean likedByMe = viewerId != null && postLikeRepository.existsByPostIdAndUserId(postId, viewerId);
        return postMapper.toResponse(post, authorName, likedByMe);
    }

    @Transactional(readOnly = true)
    @Cacheable(
            cacheNames = "feedCache",
            key = "T(io.github.eduardopaim.graphcognitio.cache.CacheKeys).feed(#cursor, #limit, #viewerId)"
    )
    public FeedResponse getFeed(String cursor, int limit, UUID viewerId) {
        CursorKey cursorKey = decodeCursor(cursor);
        Pageable pageable = PageRequest.of(0, limit + 1);

        List<PostEntity> page;
        if (cursorKey == null) {
            page = postRepository.findRootPostsWithoutCursor(pageable);
        } else {
            page = postRepository.findRootPostsAfterCursor(
                    cursorKey.createdAt(),
                    cursorKey.id(),
                    pageable
            );
        }

        boolean hasMore = page.size() > limit;
        List<PostEntity> currentItems = hasMore ? new ArrayList<>(page.subList(0, limit)) : page;
        Map<UUID, String> authorNames = loadAuthorNames(currentItems);
        Set<UUID> likedPostIds = loadLikedPostIds(viewerId, currentItems);
        List<PostResponse> items = currentItems.stream()
                .map(post -> postMapper.toResponse(
                        post,
                        authorNames.get(post.getAuthorId()),
                        likedPostIds.contains(post.getId())
                ))
                .toList();

        String nextCursor = null;
        if (hasMore && !currentItems.isEmpty()) {
            PostEntity lastPost = currentItems.get(currentItems.size() - 1);
            nextCursor = encodeCursor(lastPost.getCreatedAt(), lastPost.getId());
        }

        return new FeedResponse(items, nextCursor);
    }

    @Transactional
    @Caching(evict = {
            @CacheEvict(cacheNames = "feedCache", allEntries = true),
            @CacheEvict(cacheNames = "graphCache", allEntries = true)
    })
    public PostResponse likePost(UUID userId, UUID postId) {
        UserEntity actor = loadRequiredUser(userId);

        PostEntity post = postRepository.findById(postId)
                .orElseThrow(() -> new ResourceNotFoundException("Post not found"));

        boolean alreadyLiked = postLikeRepository.existsByPostIdAndUserId(postId, userId);
        if (!alreadyLiked) {
            PostLikeEntity postLike = new PostLikeEntity();
            postLike.setPostId(postId);
            postLike.setUserId(userId);
            postLike.setCreatedAt(Instant.now());
            postLikeRepository.save(postLike);

            post.setLikeCount(post.getLikeCount() + 1);
            postRepository.save(post);

            auditLogService.log(
                    actor.getId(),
                    "POST_LIKED",
                    "POST",
                    postId,
                    Map.of("authorId", post.getAuthorId().toString())
            );
        }

        String authorName = findAuthorName(post.getAuthorId());
        return postMapper.toResponse(post, authorName, true);
    }

    @Transactional
    @Caching(evict = {
            @CacheEvict(cacheNames = "feedCache", allEntries = true),
            @CacheEvict(cacheNames = "graphCache", allEntries = true)
    })
    public PostResponse unlikePost(UUID userId, UUID postId) {
        UserEntity actor = loadRequiredUser(userId);

        PostEntity post = postRepository.findById(postId)
                .orElseThrow(() -> new ResourceNotFoundException("Post not found"));

        postLikeRepository.findByPostIdAndUserId(postId, userId).ifPresent(existingLike -> {
            postLikeRepository.delete(existingLike);
            post.setLikeCount(Math.max(0, post.getLikeCount() - 1));
            postRepository.save(post);
            auditLogService.log(
                    actor.getId(),
                    "POST_UNLIKED",
                    "POST",
                    postId,
                    Map.of("authorId", post.getAuthorId().toString())
            );
        });

        String authorName = findAuthorName(post.getAuthorId());
        return postMapper.toResponse(post, authorName, false);
    }

    @Transactional
    @Caching(evict = {
            @CacheEvict(cacheNames = "feedCache", allEntries = true),
            @CacheEvict(cacheNames = "graphCache", allEntries = true)
    })
    public PostResponse updatePost(UUID actorUserId, UUID postId, UpdatePostRequest request) {
        UserEntity actor = loadRequiredUser(actorUserId);
        PostEntity post = postRepository.findById(postId)
                .orElseThrow(() -> new ResourceNotFoundException("Post not found"));

        if (!actor.getId().equals(post.getAuthorId())) {
            throw new ForbiddenException("You can only edit your own posts");
        }

        post.setContent(request.content().trim());
        PostEntity savedPost = postRepository.save(post);

        auditLogService.log(
                actor.getId(),
                "POST_UPDATED",
                "POST",
                savedPost.getId(),
                Map.of("authorId", savedPost.getAuthorId().toString())
        );

        boolean likedByMe = postLikeRepository.existsByPostIdAndUserId(savedPost.getId(), actor.getId());
        String authorName = findAuthorName(savedPost.getAuthorId());
        return postMapper.toResponse(savedPost, authorName, likedByMe);
    }

    @Transactional
    @Caching(evict = {
            @CacheEvict(cacheNames = "feedCache", allEntries = true),
            @CacheEvict(cacheNames = "graphCache", allEntries = true)
    })
    public void deletePost(UUID actorUserId, UUID postId) {
        UserEntity actor = loadRequiredUser(actorUserId);
        PostEntity post = postRepository.findById(postId)
                .orElseThrow(() -> new ResourceNotFoundException("Post not found"));

        boolean isAuthor = actor.getId().equals(post.getAuthorId());
        boolean isModerator = actor.getRole() == UserRole.MOD || actor.getRole() == UserRole.ADMIN;
        if (!isAuthor && !isModerator) {
            throw new ForbiddenException("You do not have permission to delete this post");
        }

        if (postRepository.existsByParentId(postId)) {
            throw new ConflictException("Cannot delete post with replies");
        }

        if (post.getParentId() != null) {
            decrementParentReplyCount(post.getParentId());
            edgeRepository.deleteBySourcePostId(postId);
        }

        postLikeRepository.deleteByPostId(postId);
        postRepository.delete(post);

        auditLogService.log(
                actor.getId(),
                "POST_DELETED",
                "POST",
                postId,
                Map.of(
                        "postAuthorId", post.getAuthorId().toString(),
                        "deletedByRole", actor.getRole().name()
                )
        );
    }

    private UUID resolveRootId(PostEntity parentPost) {
        if (parentPost.getParentId() == null) {
            return parentPost.getId();
        }
        return parentPost.getRootId();
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

    private Set<UUID> loadLikedPostIds(UUID viewerId, List<PostEntity> posts) {
        if (viewerId == null || posts.isEmpty()) {
            return Set.of();
        }

        Set<UUID> postIds = posts.stream()
                .map(PostEntity::getId)
                .collect(Collectors.toSet());

        return postLikeRepository.findLikedPostIdsByUserIdAndPostIdIn(viewerId, postIds);
    }

    private String findAuthorName(UUID authorId) {
        return userRepository.findById(authorId)
                .map(UserEntity::getName)
                .orElseThrow(() -> new ResourceNotFoundException("Author not found"));
    }

    private UserEntity loadRequiredUser(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new UnauthorizedException("Authenticated user not found"));
    }

    private void decrementParentReplyCount(UUID parentId) {
        postRepository.findById(parentId).ifPresent(parent -> {
            parent.setReplyCount(Math.max(0, parent.getReplyCount() - 1));
            postRepository.save(parent);
        });
    }

    private CursorKey decodeCursor(String cursor) {
        if (!StringUtils.hasText(cursor)) {
            return null;
        }

        try {
            String normalized = normalizeBase64(cursor);
            byte[] decoded = Base64.getUrlDecoder().decode(normalized);
            String payload = new String(decoded, StandardCharsets.UTF_8);
            String[] parts = payload.split("\\|", -1);
            if (parts.length != 2) {
                throw new BadRequestException("Invalid cursor");
            }

            Instant createdAt = Instant.parse(parts[0]);
            UUID id = UUID.fromString(parts[1]);
            return new CursorKey(createdAt, id);
        } catch (IllegalArgumentException | BadRequestException ex) {
            throw new BadRequestException("Invalid cursor");
        }
    }

    private String encodeCursor(Instant createdAt, UUID id) {
        String raw = createdAt.toString() + "|" + id;
        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    private String normalizeBase64(String value) {
        int padding = (4 - (value.length() % 4)) % 4;
        return value + "=".repeat(padding);
    }

    private record CursorKey(Instant createdAt, UUID id) {
    }
}
