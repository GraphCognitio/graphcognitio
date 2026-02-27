package io.github.eduardopaim.graphcognitio.posts.controller;

import io.github.eduardopaim.graphcognitio.posts.dto.FeedResponse;
import io.github.eduardopaim.graphcognitio.posts.service.PostService;
import io.github.eduardopaim.graphcognitio.security.AuthenticatedUser;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
public class FeedController {

    private final PostService postService;

    public FeedController(PostService postService) {
        this.postService = postService;
    }

    @GetMapping("/feed")
    public ResponseEntity<FeedResponse> getFeed(@AuthenticationPrincipal AuthenticatedUser authenticatedUser,
                                                @RequestParam(required = false) String cursor,
                                                @RequestParam(defaultValue = "20") @Min(1) @Max(50) int limit) {
        UUID viewerId = authenticatedUser == null ? null : authenticatedUser.userId();
        return ResponseEntity.ok(postService.getFeed(cursor, limit, viewerId));
    }
}
