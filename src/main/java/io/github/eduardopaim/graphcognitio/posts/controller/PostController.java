package io.github.eduardopaim.graphcognitio.posts.controller;

import io.github.eduardopaim.graphcognitio.exception.UnauthorizedException;
import io.github.eduardopaim.graphcognitio.idempotency.IdempotencyService;
import io.github.eduardopaim.graphcognitio.posts.dto.CreatePostRequest;
import io.github.eduardopaim.graphcognitio.posts.dto.PostResponse;
import io.github.eduardopaim.graphcognitio.posts.dto.UpdatePostRequest;
import io.github.eduardopaim.graphcognitio.posts.service.PostService;
import io.github.eduardopaim.graphcognitio.security.AuthenticatedUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/posts")
public class PostController {

    private final PostService postService;
    private final IdempotencyService idempotencyService;

    public PostController(PostService postService, IdempotencyService idempotencyService) {
        this.postService = postService;
        this.idempotencyService = idempotencyService;
    }

    @PostMapping
    public ResponseEntity<PostResponse> createPost(@AuthenticationPrincipal AuthenticatedUser authenticatedUser,
                                                   @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
                                                   HttpServletRequest httpServletRequest,
                                                   @Valid @RequestBody CreatePostRequest request) {
        if (authenticatedUser == null) {
            throw new UnauthorizedException("Unauthorized");
        }

        PostResponse response = idempotencyService.execute(
                authenticatedUser.userId(),
                idempotencyKey,
                httpServletRequest.getRequestURI(),
                request,
                201,
                PostResponse.class,
                () -> postService.createRootPost(authenticatedUser.userId(), request)
        );
        URI location = ServletUriComponentsBuilder
                .fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(response.id())
                .toUri();

        return ResponseEntity.created(location).body(response);
    }

    @PostMapping("/{id}/reply")
    public ResponseEntity<PostResponse> createReply(@AuthenticationPrincipal AuthenticatedUser authenticatedUser,
                                                    @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
                                                    HttpServletRequest httpServletRequest,
                                                    @PathVariable UUID id,
                                                    @Valid @RequestBody CreatePostRequest request) {
        if (authenticatedUser == null) {
            throw new UnauthorizedException("Unauthorized");
        }

        PostResponse response = idempotencyService.execute(
                authenticatedUser.userId(),
                idempotencyKey,
                httpServletRequest.getRequestURI(),
                request,
                201,
                PostResponse.class,
                () -> postService.createReply(authenticatedUser.userId(), id, request)
        );
        URI location = URI.create("/posts/" + response.id());

        return ResponseEntity.created(location).body(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<PostResponse> getPost(@AuthenticationPrincipal AuthenticatedUser authenticatedUser,
                                                @PathVariable UUID id) {
        UUID viewerId = authenticatedUser == null ? null : authenticatedUser.userId();
        return ResponseEntity.ok(postService.getPostById(id, viewerId));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<PostResponse> updatePost(@AuthenticationPrincipal AuthenticatedUser authenticatedUser,
                                                   @PathVariable UUID id,
                                                   @Valid @RequestBody UpdatePostRequest request) {
        if (authenticatedUser == null) {
            throw new UnauthorizedException("Unauthorized");
        }
        return ResponseEntity.ok(postService.updatePost(authenticatedUser.userId(), id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletePost(@AuthenticationPrincipal AuthenticatedUser authenticatedUser,
                                           @PathVariable UUID id) {
        if (authenticatedUser == null) {
            throw new UnauthorizedException("Unauthorized");
        }
        postService.deletePost(authenticatedUser.userId(), id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/likes")
    public ResponseEntity<PostResponse> likePost(@AuthenticationPrincipal AuthenticatedUser authenticatedUser,
                                                 @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
                                                 HttpServletRequest httpServletRequest,
                                                 @PathVariable UUID id) {
        if (authenticatedUser == null) {
            throw new UnauthorizedException("Unauthorized");
        }

        PostResponse response = idempotencyService.execute(
                authenticatedUser.userId(),
                idempotencyKey,
                httpServletRequest.getRequestURI(),
                null,
                200,
                PostResponse.class,
                () -> postService.likePost(authenticatedUser.userId(), id)
        );
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{id}/likes")
    public ResponseEntity<PostResponse> unlikePost(@AuthenticationPrincipal AuthenticatedUser authenticatedUser,
                                                   @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
                                                   HttpServletRequest httpServletRequest,
                                                   @PathVariable UUID id) {
        if (authenticatedUser == null) {
            throw new UnauthorizedException("Unauthorized");
        }

        PostResponse response = idempotencyService.execute(
                authenticatedUser.userId(),
                idempotencyKey,
                httpServletRequest.getRequestURI(),
                null,
                200,
                PostResponse.class,
                () -> postService.unlikePost(authenticatedUser.userId(), id)
        );
        return ResponseEntity.ok(response);
    }
}
