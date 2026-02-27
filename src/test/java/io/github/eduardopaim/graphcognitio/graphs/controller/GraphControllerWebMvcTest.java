package io.github.eduardopaim.graphcognitio.graphs.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.eduardopaim.graphcognitio.exception.ResourceNotFoundException;
import io.github.eduardopaim.graphcognitio.graphs.dto.ConversationEdgeResponse;
import io.github.eduardopaim.graphcognitio.graphs.dto.ConversationGraphResponse;
import io.github.eduardopaim.graphcognitio.graphs.dto.ConversationNodeResponse;
import io.github.eduardopaim.graphcognitio.graphs.service.GraphService;
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
        controllers = GraphController.class,
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
class GraphControllerWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private GraphService graphService;

    @Test
    void getConversationReturnsNodesAndEdges() throws Exception {
        UUID rootId = UUID.fromString("2fbf8f22-5485-4f95-a9c0-1723a49bfd7c");
        UUID replyId = UUID.fromString("bbdd2f10-c028-4058-b7f6-f0e74f0ed78d");
        UUID authorId = UUID.fromString("d6a63297-3f0f-4da4-9014-097bb6ebcb55");
        UUID edgeId = UUID.fromString("dc4a2480-0619-4512-8733-fb30bcf8dca8");
        Instant rootCreatedAt = Instant.parse("2026-02-26T21:10:00Z");
        Instant replyCreatedAt = Instant.parse("2026-02-26T21:11:00Z");

        ConversationGraphResponse response = new ConversationGraphResponse(
                List.of(
                        new ConversationNodeResponse(
                                rootId, authorId, "Eduardo", "root post", rootCreatedAt, null, rootId, 1
                        ),
                        new ConversationNodeResponse(
                                replyId, authorId, "Eduardo", "reply post", replyCreatedAt, rootId, rootId, 0
                        )
                ),
                List.of(
                        new ConversationEdgeResponse(
                                edgeId, replyId, rootId, "REPLY", replyCreatedAt
                        )
                )
        );
        when(graphService.getConversationGraph(rootId, 2, 120)).thenReturn(response);

        mockMvc.perform(get("/graphs/conversation/{rootId}", rootId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nodes.length()").value(2))
                .andExpect(jsonPath("$.nodes[0].id").value(rootId.toString()))
                .andExpect(jsonPath("$.nodes[0].authorId").value(authorId.toString()))
                .andExpect(jsonPath("$.nodes[0].authorName").value("Eduardo"))
                .andExpect(jsonPath("$.nodes[0].contentPreview").value("root post"))
                .andExpect(jsonPath("$.nodes[0].createdAt").value("2026-02-26T21:10:00Z"))
                .andExpect(jsonPath("$.nodes[0].parentId").doesNotExist())
                .andExpect(jsonPath("$.nodes[0].rootId").value(rootId.toString()))
                .andExpect(jsonPath("$.nodes[0].replyCount").value(1))
                .andExpect(jsonPath("$.edges.length()").value(1))
                .andExpect(jsonPath("$.edges[0].id").value(edgeId.toString()))
                .andExpect(jsonPath("$.edges[0].sourcePostId").value(replyId.toString()))
                .andExpect(jsonPath("$.edges[0].targetPostId").value(rootId.toString()))
                .andExpect(jsonPath("$.edges[0].type").value("REPLY"))
                .andExpect(jsonPath("$.edges[0].createdAt").value("2026-02-26T21:11:00Z"));
    }

    @Test
    void getConversationReturnsNotFoundWhenRootDoesNotExist() throws Exception {
        UUID rootId = UUID.fromString("2fbf8f22-5485-4f95-a9c0-1723a49bfd7c");
        when(graphService.getConversationGraph(rootId, 2, 120))
                .thenThrow(new ResourceNotFoundException("Root post not found"));

        mockMvc.perform(get("/graphs/conversation/{rootId}", rootId))
                .andExpect(status().isNotFound())
                .andExpect(header().string("Content-Type", "application/problem+json"))
                .andExpect(jsonPath("$.title").value("Not Found"))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.detail").value("Root post not found"));
    }

    @Test
    void getConversationReturnsBadRequestWhenRootIdIsInvalid() throws Exception {
        mockMvc.perform(get("/graphs/conversation/{rootId}", "invalid-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(header().string("Content-Type", "application/problem+json"))
                .andExpect(jsonPath("$.title").value("Bad Request"))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.detail").value("Invalid value for rootId"));
    }

    @Test
    void getConversationReturnsBadRequestWhenDepthIsNegative() throws Exception {
        UUID rootId = UUID.fromString("2fbf8f22-5485-4f95-a9c0-1723a49bfd7c");

        mockMvc.perform(get("/graphs/conversation/{rootId}", rootId).param("depth", "-1"))
                .andExpect(status().isBadRequest())
                .andExpect(header().string("Content-Type", "application/problem+json"))
                .andExpect(jsonPath("$.title").value("Bad Request"))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.detail").value("Validation failed"))
                .andExpect(jsonPath("$.errors.depth").exists());
    }
}
