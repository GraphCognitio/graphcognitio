package io.github.eduardopaim.graphcognitio.posts.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.eduardopaim.graphcognitio.posts.dto.CreatePostRequest;
import io.github.eduardopaim.graphcognitio.posts.dto.PostResponse;
import io.github.eduardopaim.graphcognitio.posts.service.PostService;
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
        excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = JwtAuthenticationFilter.class)
)
@AutoConfigureMockMvc(addFilters = false)
class PostControllerWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private PostService postService;

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
                0
        );
        when(postService.createRootPost(eq(userId), any(CreatePostRequest.class))).thenReturn(response);

        Authentication authentication = new UsernamePasswordAuthenticationToken(
                new AuthenticatedUser(userId),
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
                    .andExpect(jsonPath("$.replyCount").value(0));
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
                0
        );
        when(postService.getPostById(postId)).thenReturn(response);

        mockMvc.perform(get("/posts/{id}", postId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(postId.toString()))
                .andExpect(jsonPath("$.authorId").value(userId.toString()))
                .andExpect(jsonPath("$.authorName").value("Eduardo"))
                .andExpect(jsonPath("$.content").value("hello graph world"))
                .andExpect(jsonPath("$.createdAt").value("2026-02-26T21:10:00Z"))
                .andExpect(jsonPath("$.parentId").doesNotExist())
                .andExpect(jsonPath("$.rootId").value(postId.toString()))
                .andExpect(jsonPath("$.replyCount").value(0));
    }
}
