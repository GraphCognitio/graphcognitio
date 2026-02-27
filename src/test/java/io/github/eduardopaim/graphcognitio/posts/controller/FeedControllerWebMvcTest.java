package io.github.eduardopaim.graphcognitio.posts.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.eduardopaim.graphcognitio.posts.dto.FeedResponse;
import io.github.eduardopaim.graphcognitio.posts.dto.PostResponse;
import io.github.eduardopaim.graphcognitio.posts.service.PostService;
import io.github.eduardopaim.graphcognitio.observability.AuthenticatedUserMdcFilter;
import io.github.eduardopaim.graphcognitio.observability.HttpMetricsFilter;
import io.github.eduardopaim.graphcognitio.observability.RequestCorrelationFilter;
import io.github.eduardopaim.graphcognitio.ratelimit.RateLimitFilter;
import io.github.eduardopaim.graphcognitio.security.JwtAuthenticationFilter;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(
        controllers = FeedController.class,
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
class FeedControllerWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PostService postService;

    @Test
    void getFeedReturnsItemsAndNextCursor() throws Exception {
        UUID authorId = UUID.fromString("d6a63297-3f0f-4da4-9014-097bb6ebcb55");
        UUID postId = UUID.fromString("2fbf8f22-5485-4f95-a9c0-1723a49bfd7c");
        Instant createdAt = Instant.parse("2026-02-26T21:10:00Z");

        FeedResponse response = new FeedResponse(
                List.of(new PostResponse(postId, authorId, "Eduardo", "root post", createdAt, null, postId, 0, 3, true)),
                "MjAyNi0wMi0yNlQyMToxMDowMFp8MmZiZjhmMjItNTQ4NS00Zjk1LWE5YzAtMTcyM2E0OWJmZDdj"
        );
        when(postService.getFeed(null, 20, null)).thenReturn(response);

        mockMvc.perform(get("/feed"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].id").value(postId.toString()))
                .andExpect(jsonPath("$.items[0].authorId").value(authorId.toString()))
                .andExpect(jsonPath("$.items[0].authorName").value("Eduardo"))
                .andExpect(jsonPath("$.items[0].content").value("root post"))
                .andExpect(jsonPath("$.items[0].createdAt").value("2026-02-26T21:10:00Z"))
                .andExpect(jsonPath("$.items[0].parentId").doesNotExist())
                .andExpect(jsonPath("$.items[0].rootId").value(postId.toString()))
                .andExpect(jsonPath("$.items[0].replyCount").value(0))
                .andExpect(jsonPath("$.items[0].likeCount").value(3))
                .andExpect(jsonPath("$.items[0].likedByMe").value(true))
                .andExpect(jsonPath("$.nextCursor").value(response.nextCursor()));
    }

    @Test
    void getFeedReturnsNullNextCursorWhenFinished() throws Exception {
        FeedResponse response = new FeedResponse(List.of(), null);
        when(postService.getFeed(null, 20, null)).thenReturn(response);

        mockMvc.perform(get("/feed"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(0))
                .andExpect(jsonPath("$.nextCursor").isEmpty());
    }

    @Test
    void getFeedReturnsBadRequestWhenLimitIsAboveMax() throws Exception {
        mockMvc.perform(get("/feed").param("limit", "51"))
                .andExpect(status().isBadRequest())
                .andExpect(header().string("Content-Type", "application/problem+json"))
                .andExpect(jsonPath("$.title").value("Bad Request"))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.detail").value("Validation failed"))
                .andExpect(jsonPath("$.errors.limit").exists());
    }
}
