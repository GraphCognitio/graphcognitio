package io.github.eduardopaim.graphcognitio.admin.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.eduardopaim.graphcognitio.admin.dto.EmbeddingReindexHistoryEntry;
import io.github.eduardopaim.graphcognitio.admin.dto.EmbeddingReindexResponse;
import io.github.eduardopaim.graphcognitio.admin.dto.EmbeddingsStatusResponse;
import io.github.eduardopaim.graphcognitio.admin.service.AdminService;
import io.github.eduardopaim.graphcognitio.entity.UserRole;
import io.github.eduardopaim.graphcognitio.observability.AuthenticatedUserMdcFilter;
import io.github.eduardopaim.graphcognitio.observability.HttpMetricsFilter;
import io.github.eduardopaim.graphcognitio.observability.RequestCorrelationFilter;
import io.github.eduardopaim.graphcognitio.ratelimit.RateLimitFilter;
import io.github.eduardopaim.graphcognitio.security.AuthenticatedUser;
import io.github.eduardopaim.graphcognitio.security.JwtAuthenticationFilter;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(
        controllers = AdminEmbeddingsController.class,
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
class AdminEmbeddingsControllerWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AdminService adminService;

    @Test
    void statusReturnsEmbeddingHealthSnapshot() throws Exception {
        UUID actorId = UUID.fromString("d6a63297-3f0f-4da4-9014-097bb6ebcb55");
        when(adminService.embeddingsStatus(eq(actorId)))
                .thenReturn(new EmbeddingsStatusResponse(
                        "UP",
                        true,
                        "openai",
                        "text-embedding-3-small",
                        1536,
                        200,
                        true,
                        true,
                        "https://api.openai.com",
                        "/v1/embeddings",
                        null
                ));

        Authentication authentication = new UsernamePasswordAuthenticationToken(
                new AuthenticatedUser(actorId, UserRole.ADMIN),
                null,
                Collections.emptyList()
        );
        SecurityContextHolder.getContext().setAuthentication(authentication);

        try {
            mockMvc.perform(get("/admin/embeddings/status")
                            .with(authentication(authentication)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("UP"))
                    .andExpect(jsonPath("$.provider").value("openai"))
                    .andExpect(jsonPath("$.apiKeyConfigured").value(true));
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    void historyReturnsEmbeddingsReindexEvents() throws Exception {
        UUID actorId = UUID.fromString("d6a63297-3f0f-4da4-9014-097bb6ebcb55");
        UUID eventId = UUID.fromString("0c2b75c4-aec8-4f62-9ab4-e3a8c5b8a92d");

        when(adminService.embeddingsHistory(eq(actorId), eq(5)))
                .thenReturn(List.of(new EmbeddingReindexHistoryEntry(
                        eventId,
                        Instant.parse("2026-03-01T18:00:00Z"),
                        actorId,
                        "Admin Operator",
                        "missing",
                        42,
                        "semantic-hash-v3",
                        192
                )));

        Authentication authentication = new UsernamePasswordAuthenticationToken(
                new AuthenticatedUser(actorId, UserRole.ADMIN),
                null,
                Collections.emptyList()
        );
        SecurityContextHolder.getContext().setAuthentication(authentication);

        try {
            mockMvc.perform(get("/admin/embeddings/history")
                            .with(authentication(authentication)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].id").value(eventId.toString()))
                    .andExpect(jsonPath("$[0].actorName").value("Admin Operator"))
                    .andExpect(jsonPath("$[0].scope").value("missing"));
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    void reindexReturnsEmbeddingStatus() throws Exception {
        UUID actorId = UUID.fromString("d6a63297-3f0f-4da4-9014-097bb6ebcb55");
        when(adminService.reindexEmbeddings(eq(actorId), eq("missing"), eq(120)))
                .thenReturn(new EmbeddingReindexResponse("missing", 42, "semantic-hash-v3", 192));

        Authentication authentication = new UsernamePasswordAuthenticationToken(
                new AuthenticatedUser(actorId, UserRole.ADMIN),
                null,
                Collections.emptyList()
        );
        SecurityContextHolder.getContext().setAuthentication(authentication);

        try {
            mockMvc.perform(post("/admin/embeddings/reindex")
                            .with(authentication(authentication))
                            .queryParam("scope", "missing")
                            .queryParam("limit", "120"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.scope").value("missing"))
                    .andExpect(jsonPath("$.reindexedCount").value(42))
                    .andExpect(jsonPath("$.model").value("semantic-hash-v3"))
                    .andExpect(jsonPath("$.dimensions").value(192));
        } finally {
            SecurityContextHolder.clearContext();
        }
    }
}
