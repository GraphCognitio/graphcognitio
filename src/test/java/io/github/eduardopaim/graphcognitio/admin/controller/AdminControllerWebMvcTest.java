package io.github.eduardopaim.graphcognitio.admin.controller;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.eduardopaim.graphcognitio.admin.dto.AdminUserResponse;
import io.github.eduardopaim.graphcognitio.admin.dto.UpdateUserRoleRequest;
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
        controllers = AdminController.class,
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
class AdminControllerWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AdminService adminService;

    @Test
    void updateUserRoleReturnsUpdatedUser() throws Exception {
        UUID actorId = UUID.fromString("d6a63297-3f0f-4da4-9014-097bb6ebcb55");
        UUID targetId = UUID.fromString("338ed4fe-f51d-423d-8fd3-c35c4f740fc7");

        AdminUserResponse response = new AdminUserResponse(
                targetId,
                "Target User",
                "target@example.com",
                UserRole.MOD,
                Instant.parse("2026-02-26T21:10:00Z")
        );
        when(adminService.updateUserRole(eq(actorId), eq(targetId), eq(UserRole.MOD))).thenReturn(response);

        Authentication authentication = new UsernamePasswordAuthenticationToken(
                new AuthenticatedUser(actorId, UserRole.ADMIN),
                null,
                Collections.emptyList()
        );
        SecurityContextHolder.getContext().setAuthentication(authentication);

        try {
            mockMvc.perform(patch("/admin/users/{id}/role", targetId)
                            .with(authentication(authentication))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(new UpdateUserRoleRequest(UserRole.MOD))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(targetId.toString()))
                    .andExpect(jsonPath("$.role").value("MOD"));
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    void updateUserRoleReturnsBadRequestWhenRoleIsMissing() throws Exception {
        UUID actorId = UUID.fromString("d6a63297-3f0f-4da4-9014-097bb6ebcb55");
        UUID targetId = UUID.fromString("338ed4fe-f51d-423d-8fd3-c35c4f740fc7");

        Authentication authentication = new UsernamePasswordAuthenticationToken(
                new AuthenticatedUser(actorId, UserRole.ADMIN),
                null,
                Collections.emptyList()
        );
        SecurityContextHolder.getContext().setAuthentication(authentication);

        try {
            mockMvc.perform(patch("/admin/users/{id}/role", targetId)
                            .with(authentication(authentication))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.title").value("Bad Request"))
                    .andExpect(jsonPath("$.status").value(400));
        } finally {
            SecurityContextHolder.clearContext();
        }
    }
}
