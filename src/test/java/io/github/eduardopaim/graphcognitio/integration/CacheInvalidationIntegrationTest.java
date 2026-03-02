package io.github.eduardopaim.graphcognitio.integration;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.eduardopaim.graphcognitio.cache.CacheKeys;
import io.github.eduardopaim.graphcognitio.graphs.GraphMode;
import io.github.eduardopaim.graphcognitio.posts.dto.FeedSort;
import java.nio.charset.StandardCharsets;
import java.util.TimeZone;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CacheInvalidationIntegrationTest {

    private static final String TEST_JWT_SECRET = "graphcognitio-dev-secret-key-256bits-minimum-length";

    static {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
    }

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(PgvectorTestSupport.PGVECTOR_POSTGRES)
            .withDatabaseName("graphcognitio")
            .withUsername("graphcognitio")
            .withPassword("graphcognitio_dev_password");

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("app.jwt.secret", () -> TEST_JWT_SECRET);
        registry.add("app.jwt.exp-minutes", () -> "60");
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private CacheManager cacheManager;

    @Test
    void createRootPostEvictsFeedCache() throws Exception {
        AuthContext auth = registerAndLogin();
        Cache feedCache = requireCache("feedCache");
        String feedKey = CacheKeys.feed(null, 20, auth.userId(), FeedSort.RECENT);

        assertNull(feedCache.get(feedKey));

        mockMvc.perform(get("/feed")
                        .header(AUTHORIZATION, bearer(auth.accessToken()))
                        .param("limit", "20"))
                .andExpect(status().isOk());

        assertNotNull(feedCache.get(feedKey));

        mockMvc.perform(post("/posts")
                        .header(AUTHORIZATION, bearer(auth.accessToken()))
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "content": "Root post invalidates feed cache"
                                }
                                """))
                .andExpect(status().isCreated());

        assertNull(feedCache.get(feedKey));
    }

    @Test
    void createReplyEvictsConversationGraphCache() throws Exception {
        AuthContext auth = registerAndLogin();
        Cache graphCache = requireCache("graphCache");

        String rootId = createRootPost(auth.accessToken(), "Root post for graph cache invalidation");
        createReply(auth.accessToken(), rootId, "First reply");

        String graphKey = CacheKeys.graph(UUID.fromString(rootId), 2, 120, GraphMode.CONVERSATION);
        assertNull(graphCache.get(graphKey));

        mockMvc.perform(get("/graphs/conversation/{rootId}", rootId)
                        .header(AUTHORIZATION, bearer(auth.accessToken()))
                        .param("depth", "2")
                        .param("limit", "120"))
                .andExpect(status().isOk());

        assertNotNull(graphCache.get(graphKey));

        createReply(auth.accessToken(), rootId, "Second reply should evict graph cache");

        assertNull(graphCache.get(graphKey));
    }

    private AuthContext registerAndLogin() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String email = "cache." + suffix + "@example.com";
        String password = "StrongPassword123";

        MvcResult registerResult = mockMvc.perform(post("/auth/register")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Cache Integration",
                                  "email": "%s",
                                  "password": "%s"
                                }
                                """.formatted(email, password)))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode registerJson = readJson(registerResult);
        UUID userId = UUID.fromString(registerJson.get("user").get("id").asText());

        MvcResult loginResult = mockMvc.perform(post("/auth/login")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "%s",
                                  "password": "%s"
                                }
                                """.formatted(email, password)))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode loginJson = readJson(loginResult);
        String accessToken = loginJson.get("accessToken").asText();
        return new AuthContext(accessToken, userId);
    }

    private String createRootPost(String accessToken, String content) throws Exception {
        MvcResult result = mockMvc.perform(post("/posts")
                        .header(AUTHORIZATION, bearer(accessToken))
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "content": "%s"
                                }
                                """.formatted(content)))
                .andExpect(status().isCreated())
                .andReturn();

        return readJson(result).get("id").asText();
    }

    private void createReply(String accessToken, String parentId, String content) throws Exception {
        mockMvc.perform(post("/posts/{id}/reply", parentId)
                        .header(AUTHORIZATION, bearer(accessToken))
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "content": "%s"
                                }
                                """.formatted(content)))
                .andExpect(status().isCreated());
    }

    private Cache requireCache(String cacheName) {
        Cache cache = cacheManager.getCache(cacheName);
        assertNotNull(cache, "Expected cache to be configured: " + cacheName);
        return cache;
    }

    private JsonNode readJson(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }

    private record AuthContext(String accessToken, UUID userId) {
    }
}
