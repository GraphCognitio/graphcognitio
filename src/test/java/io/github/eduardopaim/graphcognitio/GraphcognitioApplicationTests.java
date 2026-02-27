package io.github.eduardopaim.graphcognitio;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.eduardopaim.graphcognitio.auth.controller.AuthController;
import io.github.eduardopaim.graphcognitio.auth.dto.AuthResponse;
import io.github.eduardopaim.graphcognitio.auth.dto.AuthUserResponse;
import io.github.eduardopaim.graphcognitio.auth.dto.LoginRequest;
import io.github.eduardopaim.graphcognitio.auth.dto.RegisterRequest;
import io.github.eduardopaim.graphcognitio.auth.service.AuthService;
import io.github.eduardopaim.graphcognitio.entity.UserRole;
import io.github.eduardopaim.graphcognitio.observability.AuthenticatedUserMdcFilter;
import io.github.eduardopaim.graphcognitio.observability.HttpMetricsFilter;
import io.github.eduardopaim.graphcognitio.observability.RequestCorrelationFilter;
import io.github.eduardopaim.graphcognitio.ratelimit.RateLimitFilter;
import io.github.eduardopaim.graphcognitio.security.JwtAuthenticationFilter;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(
        controllers = AuthController.class,
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
class GraphcognitioApplicationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AuthService authService;

    @Test
    void registerReturnsCreatedWithTokenAndUser() throws Exception {
        UUID userId = UUID.fromString("d6a63297-3f0f-4da4-9014-097bb6ebcb55");
        Instant createdAt = Instant.parse("2026-02-26T21:10:00Z");
        AuthResponse response = new AuthResponse(
                "access-token",
                new AuthUserResponse(userId, "Eduardo", "eduardo@example.com", UserRole.USER, createdAt)
        );

        when(authService.register(any(RegisterRequest.class))).thenReturn(response);

        RegisterRequest request = new RegisterRequest("Eduardo", "eduardo@example.com", "secret123");

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accessToken").value("access-token"))
                .andExpect(jsonPath("$.user.id").value(userId.toString()))
                .andExpect(jsonPath("$.user.name").value("Eduardo"))
                .andExpect(jsonPath("$.user.email").value("eduardo@example.com"))
                .andExpect(jsonPath("$.user.role").value("USER"))
                .andExpect(jsonPath("$.user.createdAt").value("2026-02-26T21:10:00Z"));
    }

    @Test
    void loginReturnsOkWithTokenAndUser() throws Exception {
        UUID userId = UUID.fromString("d6a63297-3f0f-4da4-9014-097bb6ebcb55");
        Instant createdAt = Instant.parse("2026-02-26T21:10:00Z");
        AuthResponse response = new AuthResponse(
                "access-token",
                new AuthUserResponse(userId, "Eduardo", "eduardo@example.com", UserRole.USER, createdAt)
        );

        when(authService.login(any(LoginRequest.class))).thenReturn(response);

        LoginRequest request = new LoginRequest("eduardo@example.com", "secret123");

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("access-token"))
                .andExpect(jsonPath("$.user.id").value(userId.toString()))
                .andExpect(jsonPath("$.user.name").value("Eduardo"))
                .andExpect(jsonPath("$.user.email").value("eduardo@example.com"))
                .andExpect(jsonPath("$.user.role").value("USER"))
                .andExpect(jsonPath("$.user.createdAt").value("2026-02-26T21:10:00Z"));
    }
}
