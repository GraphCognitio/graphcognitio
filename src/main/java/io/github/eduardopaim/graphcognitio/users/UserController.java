package io.github.eduardopaim.graphcognitio.users;

import io.github.eduardopaim.graphcognitio.posts.dto.FeedResponse;
import io.github.eduardopaim.graphcognitio.posts.service.PostService;
import io.github.eduardopaim.graphcognitio.users.dto.UserProfileResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import jakarta.validation.Valid;
import io.github.eduardopaim.graphcognitio.users.dto.UpdateProfileRequest;

@RestController
@RequestMapping("/users")
@Tag(name = "Users", description = "User profile endpoints")
public class UserController {

    private final UserService userService;
    private final PostService postService;

    public UserController(UserService userService, PostService postService) {
        this.userService = userService;
        this.postService = postService;
    }

    @GetMapping("/{username}")
    @Operation(summary = "Get user profile by username")
    public ResponseEntity<UserProfileResponse> getUserProfile(
            @Parameter(description = "Username") @PathVariable String username) {
        return ResponseEntity.ok(userService.getUserProfile(username));
    }

    @GetMapping("/{username}/posts")
    @Operation(summary = "Get user paginated posts")
    public ResponseEntity<FeedResponse> getUserPosts(
            @Parameter(description = "Username") @PathVariable String username,
            @Parameter(description = "Pagination cursor") @RequestParam(required = false) String cursor,
            @Parameter(description = "Maximum number of items to return") @RequestParam(defaultValue = "20") int limit,
            @Parameter(hidden = true) @AuthenticationPrincipal io.github.eduardopaim.graphcognitio.security.AuthenticatedUser user) {
        java.util.UUID viewerId = user != null ? user.userId() : null;
        return ResponseEntity.ok(postService.getUserFeed(username, cursor, limit, viewerId));
    }

    @PatchMapping("/me")
    @Operation(summary = "Update current user profile")
    public ResponseEntity<UserProfileResponse> updateProfile(
            @Parameter(hidden = true) @AuthenticationPrincipal io.github.eduardopaim.graphcognitio.security.AuthenticatedUser user,
            @Valid @RequestBody UpdateProfileRequest request) {
        if (user == null) {
            return ResponseEntity.status(401).build();
        }
        return ResponseEntity.ok(userService.updateProfile(user.userId(), request));
    }
}
