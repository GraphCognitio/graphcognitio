package io.github.eduardopaim.graphcognitio.posts.controller;

import io.github.eduardopaim.graphcognitio.exception.UnauthorizedException;
import io.github.eduardopaim.graphcognitio.posts.dto.CreatePostRequest;
import io.github.eduardopaim.graphcognitio.posts.dto.PostResponse;
import io.github.eduardopaim.graphcognitio.posts.service.PostService;
import io.github.eduardopaim.graphcognitio.security.AuthenticatedUser;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/posts")
public class PostController {

    private final PostService postService;

    public PostController(PostService postService) {
        this.postService = postService;
    }

    @PostMapping
    public ResponseEntity<PostResponse> createPost(@AuthenticationPrincipal AuthenticatedUser authenticatedUser,
                                                   @Valid @RequestBody CreatePostRequest request) {
        if (authenticatedUser == null) {
            throw new UnauthorizedException("Unauthorized");
        }

        PostResponse response = postService.createRootPost(authenticatedUser.userId(), request);
        URI location = ServletUriComponentsBuilder
                .fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(response.id())
                .toUri();

        return ResponseEntity.created(location).body(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<PostResponse> getPost(@PathVariable UUID id) {
        return ResponseEntity.ok(postService.getPostById(id));
    }
}
