package io.github.eduardopaim.graphcognitio.ratelimit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.eduardopaim.graphcognitio.entity.UserRole;
import io.github.eduardopaim.graphcognitio.security.AuthenticatedUser;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

class RateLimitFilterTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @AfterEach
    void clearSecurity() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void returns429ForAuthLoginAfterLimit() throws Exception {
        AtomicLong now = new AtomicLong(10_000);
        RateLimitService service = new RateLimitService(now::get);
        RateLimitProperties properties = new RateLimitProperties();
        properties.getAuthLogin().setLimit(1);
        properties.getAuthLogin().setWindowSeconds(60);
        RateLimitFilter filter = new RateLimitFilter(service, properties, objectMapper);

        MockHttpServletRequest firstRequest = new MockHttpServletRequest("POST", "/auth/login");
        firstRequest.setRemoteAddr("127.0.0.1");
        MockHttpServletResponse firstResponse = new MockHttpServletResponse();
        filter.doFilter(firstRequest, firstResponse, new MockFilterChain());

        MockHttpServletRequest secondRequest = new MockHttpServletRequest("POST", "/auth/login");
        secondRequest.setRemoteAddr("127.0.0.1");
        MockHttpServletResponse secondResponse = new MockHttpServletResponse();
        filter.doFilter(secondRequest, secondResponse, new MockFilterChain());

        assertEquals(429, secondResponse.getStatus());
        assertEquals("application/problem+json", secondResponse.getContentType());
        assertNotNull(secondResponse.getHeader("Retry-After"));
        assertEquals("1", secondResponse.getHeader("X-RateLimit-Limit"));
        assertEquals("0", secondResponse.getHeader("X-RateLimit-Remaining"));

        JsonNode body = objectMapper.readTree(secondResponse.getContentAsString(StandardCharsets.UTF_8));
        assertEquals("Too Many Requests", body.get("title").asText());
        assertEquals("Rate limit exceeded", body.get("detail").asText());
    }

    @Test
    void usesAuthenticatedUserKeyForPostCreation() throws Exception {
        AtomicLong now = new AtomicLong(5_000);
        RateLimitService service = new RateLimitService(now::get);
        RateLimitProperties properties = new RateLimitProperties();
        properties.getCreatePost().setLimit(1);
        properties.getCreatePost().setWindowSeconds(60);
        RateLimitFilter filter = new RateLimitFilter(service, properties, objectMapper);

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        new AuthenticatedUser(
                                java.util.UUID.fromString("d6a63297-3f0f-4da4-9014-097bb6ebcb55"),
                                UserRole.USER
                        ),
                        null,
                        Collections.emptyList()
                )
        );

        MockHttpServletRequest firstRequest = new MockHttpServletRequest("POST", "/posts");
        firstRequest.setRemoteAddr("127.0.0.1");
        MockHttpServletResponse firstResponse = new MockHttpServletResponse();
        filter.doFilter(firstRequest, firstResponse, new MockFilterChain());

        MockHttpServletRequest secondRequest = new MockHttpServletRequest("POST", "/posts");
        secondRequest.setRemoteAddr("127.0.0.1");
        MockHttpServletResponse secondResponse = new MockHttpServletResponse();
        filter.doFilter(secondRequest, secondResponse, new MockFilterChain());

        assertEquals(429, secondResponse.getStatus());
        assertTrue(Long.parseLong(secondResponse.getHeader("Retry-After")) > 0);
    }
}
