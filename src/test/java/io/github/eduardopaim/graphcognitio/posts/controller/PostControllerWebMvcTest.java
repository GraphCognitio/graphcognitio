package io.github.eduardopaim.graphcognitio.posts.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.eduardopaim.graphcognitio.entity.UserRole;
import io.github.eduardopaim.graphcognitio.exception.ResourceNotFoundException;
import io.github.eduardopaim.graphcognitio.idempotency.IdempotencyService;
import io.github.eduardopaim.graphcognitio.observability.AuthenticatedUserMdcFilter;
import io.github.eduardopaim.graphcognitio.observability.HttpMetricsFilter;
import io.github.eduardopaim.graphcognitio.observability.RequestCorrelationFilter;
import io.github.eduardopaim.graphcognitio.posts.dto.CreatePostRequest;
import io.github.eduardopaim.graphcognitio.posts.dto.PostResponse;
import io.github.eduardopaim.graphcognitio.posts.dto.UpdatePostRequest;
import io.github.eduardopaim.graphcognitio.posts.service.PostService;
import io.github.eduardopaim.graphcognitio.ratelimit.RateLimitFilter;
import io.github.eduardopaim.graphcognitio.security.AuthenticatedUser;
import io.github.eduardopaim.graphcognitio.security.JwtAuthenticationFilter;
import java.time.Instant;
import java.util.Collections;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(
        controllers = PostController.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = {
                        JwtAuthenticationFilter.class,
                        RequestCorrelationFilter.class,
                        AuthenticatedUserMdcFilter.class,
                        HttpMetricsFilter.class,
                        RateLimitFilter.class
                }
        )
)
@AutoConfigureMockMvc(addFilters = false)
class PostControllerWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private PostService postService;

    @MockBean
    private IdempotencyService idempotencyService;

    @Test
    void createPostReturnsCreatedBodyAndLocation() throws Exception {
        UUID userId = UUID.fromString("d6a63297-3f0f-4da4-9014-097bb6ebcb55");
        UUID postId = UUID.fromString("2fbf8f22-5485-4f95-a9c0-1723a49bfd7c");
        Instant createdAt = Instant.parse("2026-02-26T21:10:00Z");

        PostResponse response = new PostResponse(
                postId,
                userId,
                "Eduardo",
                "hello graph world",
                createdAt,
                null,
                postId,
                0,
                0,
                false
        );
        when(idempotencyService.execute(eq(userId), any(), eq("/posts"), any(), eq(201), eq(PostResponse.class), any()))
                .thenReturn(response);

        Authentication authentication = new UsernamePasswordAuthenticationToken(
                new AuthenticatedUser(userId, UserRole.USER),
                null,
                Collections.emptyList()
        );
        SecurityContextHolder.getContext().setAuthentication(authentication);

        CreatePostRequest request = new CreatePostRequest("hello graph world");
        try {
            mockMvc.perform(post("/posts")
                            .with(authentication(authentication))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andExpect(header().string("Location", "http://localhost/posts/" + postId))
                    .andExpect(jsonPath("$.id").value(postId.toString()))
                    .andExpect(jsonPath("$.authorId").value(userId.toString()))
                    .andExpect(jsonPath("$.authorName").value("Eduardo"))
                    .andExpect(jsonPath("$.content").value("hello graph world"))
                    .andExpect(jsonPath("$.createdAt").value("2026-02-26T21:10:00Z"))
                    .andExpect(jsonPath("$.parentId").doesNotExist())
                    .andExpect(jsonPath("$.rootId").value(postId.toString()))
                    .andExpect(jsonPath("$.replyCount").value(0))
                    .andExpect(jsonPath("$.likeCount").value(0))
                    .andExpect(jsonPath("$.likedByMe").value(false));
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    void getPostReturnsOkBody() throws Exception {
        UUID userId = UUID.fromString("d6a63297-3f0f-4da4-9014-097bb6ebcb55");
        UUID postId = UUID.fromString("2fbf8f22-5485-4f95-a9c0-1723a49bfd7c");
        Instant createdAt = Instant.parse("2026-02-26T21:10:00Z");

        PostResponse response = new PostResponse(
                postId,
                userId,
                "Eduardo",
                "hello graph world",
                createdAt,
                null,
                postId,
                0,
                2,
                true
        );
        when(postService.getPostById(postId, null)).thenReturn(response);

        mockMvc.perform(get("/posts/{id}", postId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(postId.toString()))
                .andExpect(jsonPath("$.authorId").value(userId.toString()))
                .andExpect(jsonPath("$.authorName").value("Eduardo"))
                .andExpect(jsonPath("$.content").value("hello graph world"))
                .andExpect(jsonPath("$.createdAt").value("2026-02-26T21:10:00Z"))
                .andExpect(jsonPath("$.parentId").doesNotExist())
                .andExpect(jsonPath("$.rootId").value(postId.toString()))
                .andExpect(jsonPath("$.replyCount").value(0))
                .andExpect(jsonPath("$.likeCount").value(2))
                .andExpect(jsonPath("$.likedByMe").value(true));
    }

    @Test
    void createReplyReturnsCreatedBodyAndLocation() throws Exception {
        UUID userId = UUID.fromString("d6a63297-3f0f-4da4-9014-097bb6ebcb55");
        UUID parentId = UUID.fromString("2fbf8f22-5485-4f95-a9c0-1723a49bfd7c");
        UUID rootId = UUID.fromString("dc4a2480-0619-4512-8733-fb30bcf8dca8");
        UUID replyId = UUID.fromString("bbdd2f10-c028-4058-b7f6-f0e74f0ed78d");
        Instant createdAt = Instant.parse("2026-02-26T21:10:00Z");

        PostResponse response = new PostResponse(
                replyId,
                userId,
                "Eduardo",
                "reply content",
                createdAt,
                parentId,
                rootId,
                0,
                0,
                false
        );
        when(idempotencyService.execute(
                eq(userId),
                any(),
                eq("/posts/" + parentId + "/reply"),
                any(),
                eq(201),
                eq(PostResponse.class),
                any())
        ).thenReturn(response);

        Authentication authentication = new UsernamePasswordAuthenticationToken(
                new AuthenticatedUser(userId, UserRole.USER),
                null,
                Collections.emptyList()
        );
        SecurityContextHolder.getContext().setAuthentication(authentication);

        CreatePostRequest request = new CreatePostRequest("reply content");
        try {
            mockMvc.perform(post("/posts/{id}/reply", parentId)
                            .with(authentication(authentication))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andExpect(header().string("Location", "/posts/" + replyId))
                    .andExpect(jsonPath("$.id").value(replyId.toString()))
                    .andExpect(jsonPath("$.authorId").value(userId.toString()))
                    .andExpect(jsonPath("$.authorName").value("Eduardo"))
                    .andExpect(jsonPath("$.content").value("reply content"))
                    .andExpect(jsonPath("$.createdAt").value("2026-02-26T21:10:00Z"))
                    .andExpect(jsonPath("$.parentId").value(parentId.toString()))
                    .andExpect(jsonPath("$.rootId").value(rootId.toString()))
                    .andExpect(jsonPath("$.replyCount").value(0))
                    .andExpect(jsonPath("$.likeCount").value(0))
                    .andExpect(jsonPath("$.likedByMe").value(false));
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    void createReplyReturnsNotFoundWhenParentDoesNotExist() throws Exception {
        UUID userId = UUID.fromString("d6a63297-3f0f-4da4-9014-097bb6ebcb55");
        UUID parentId = UUID.fromString("2fbf8f22-5485-4f95-a9c0-1723a49bfd7c");

        when(idempotencyService.execute(
                eq(userId),
                any(),
                eq("/posts/" + parentId + "/reply"),
                any(),
                eq(201),
                eq(PostResponse.class),
                any()))
                .thenThrow(new ResourceNotFoundException("Post not found"));

        Authentication authentication = new UsernamePasswordAuthenticationToken(
                new AuthenticatedUser(userId, UserRole.USER),
                null,
                Collections.emptyList()
        );
        SecurityContextHolder.getContext().setAuthentication(authentication);

        CreatePostRequest request = new CreatePostRequest("reply content");
        try {
            mockMvc.perform(post("/posts/{id}/reply", parentId)
                            .with(authentication(authentication))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isNotFound())
                    .andExpect(header().string("Content-Type", "application/problem+json"))
                    .andExpect(jsonPath("$.title").value("Not Found"))
                    .andExpect(jsonPath("$.status").value(404))
                    .andExpect(jsonPath("$.detail").value("Post not found"));
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    void createReplyReturnsBadRequestWhenUuidIsInvalid() throws Exception {
        UUID userId = UUID.fromString("d6a63297-3f0f-4da4-9014-097bb6ebcb55");
        Authentication authentication = new UsernamePasswordAuthenticationToken(
                new AuthenticatedUser(userId, UserRole.USER),
                null,
                Collections.emptyList()
        );
        SecurityContextHolder.getContext().setAuthentication(authentication);

        CreatePostRequest request = new CreatePostRequest("reply content");
        try {
            mockMvc.perform(post("/posts/{id}/reply", "not-a-uuid")
                            .with(authentication(authentication))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(header().string("Content-Type", "application/problem+json"))
                    .andExpect(jsonPath("$.title").value("Bad Request"))
                    .andExpect(jsonPath("$.status").value(400))
                    .andExpect(jsonPath("$.detail").value("Invalid value for id"));
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    void likePostReturnsUpdatedBody() throws Exception {
        UUID userId = UUID.fromString("d6a63297-3f0f-4da4-9014-097bb6ebcb55");
        UUID postId = UUID.fromString("2fbf8f22-5485-4f95-a9c0-1723a49bfd7c");
        Instant createdAt = Instant.parse("2026-02-26T21:10:00Z");

        PostResponse response = new PostResponse(
                postId,
                userId,
                "Eduardo",
                "hello graph world",
                createdAt,
                null,
                postId,
                0,
                1,
                true
        );
        when(idempotencyService.execute(
                eq(userId),
                any(),
                eq("/posts/" + postId + "/likes"),
                any(),
                eq(200),
                eq(PostResponse.class),
                any())
        ).thenReturn(response);

        Authentication authentication = new UsernamePasswordAuthenticationToken(
                new AuthenticatedUser(userId, UserRole.USER),
                null,
                Collections.emptyList()
        );
        SecurityContextHolder.getContext().setAuthentication(authentication);

        try {
            mockMvc.perform(post("/posts/{id}/likes", postId).with(authentication(authentication)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(postId.toString()))
                    .andExpect(jsonPath("$.likeCount").value(1))
                    .andExpect(jsonPath("$.likedByMe").value(true));
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    void unlikePostReturnsUpdatedBody() throws Exception {
        UUID userId = UUID.fromString("d6a63297-3f0f-4da4-9014-097bb6ebcb55");
        UUID postId = UUID.fromString("2fbf8f22-5485-4f95-a9c0-1723a49bfd7c");
        Instant createdAt = Instant.parse("2026-02-26T21:10:00Z");

        PostResponse response = new PostResponse(
                postId,
                userId,
                "Eduardo",
                "hello graph world",
                createdAt,
                null,
                postId,
                0,
                0,
                false
        );
        when(idempotencyService.execute(
                eq(userId),
                any(),
                eq("/posts/" + postId + "/likes"),
                any(),
                eq(200),
                eq(PostResponse.class),
                any())
        ).thenReturn(response);

        Authentication authentication = new UsernamePasswordAuthenticationToken(
                new AuthenticatedUser(userId, UserRole.USER),
                null,
                Collections.emptyList()
        );
        SecurityContextHolder.getContext().setAuthentication(authentication);

        try {
            mockMvc.perform(delete("/posts/{id}/likes", postId).with(authentication(authentication)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(postId.toString()))
                    .andExpect(jsonPath("$.likeCount").value(0))
                    .andExpect(jsonPath("$.likedByMe").value(false));
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    void updatePostReturnsUpdatedBody() throws Exception {
        UUID userId = UUID.fromString("d6a63297-3f0f-4da4-9014-097bb6ebcb55");
        UUID postId = UUID.fromString("2fbf8f22-5485-4f95-a9c0-1723a49bfd7c");
        Instant createdAt = Instant.parse("2026-02-26T21:10:00Z");

        PostResponse response = new PostResponse(
                postId,
                userId,
                "Eduardo",
                "updated content",
                createdAt,
                null,
                postId,
                0,
                0,
                false
        );
        when(postService.updatePost(eq(userId), eq(postId), any(UpdatePostRequest.class))).thenReturn(response);

        Authentication authentication = new UsernamePasswordAuthenticationToken(
                new AuthenticatedUser(userId, UserRole.USER),
                null,
                Collections.emptyList()
        );
        SecurityContextHolder.getContext().setAuthentication(authentication);

        try {
            mockMvc.perform(patch("/posts/{id}", postId)
                            .with(authentication(authentication))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(new UpdatePostRequest("updated content"))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(postId.toString()))
                    .andExpect(jsonPath("$.content").value("updated content"));
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    void deletePostReturnsNoContent() throws Exception {
        UUID userId = UUID.fromString("d6a63297-3f0f-4da4-9014-097bb6ebcb55");
        UUID postId = UUID.fromString("2fbf8f22-5485-4f95-a9c0-1723a49bfd7c");

        Authentication authentication = new UsernamePasswordAuthenticationToken(
                new AuthenticatedUser(userId, UserRole.USER),
                null,
                Collections.emptyList()
        );
        SecurityContextHolder.getContext().setAuthentication(authentication);

        try {
            mockMvc.perform(delete("/posts/{id}", postId).with(authentication(authentication)))
                    .andExpect(status().isNoContent());
        } finally {
            SecurityContextHolder.clearContext();
        }
    }
}
