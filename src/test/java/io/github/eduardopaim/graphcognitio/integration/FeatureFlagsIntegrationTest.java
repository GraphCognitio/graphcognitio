package io.github.eduardopaim.graphcognitio.integration;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.TimeZone;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
@SpringBootTest(properties = {
        "app.features.search-enabled=false",
        "app.features.graph-expand-enabled=false"
})
@AutoConfigureMockMvc
class FeatureFlagsIntegrationTest {

    private static final String TEST_JWT_SECRET = "graphcognitio-dev-secret-key-256bits-minimum-length";

    static {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
    }

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(DockerImageName.parse("postgres:18"))
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

    @Test
    void searchEndpointReturnsNotFoundWhenFlagDisabled() throws Exception {
        AuthContext auth = registerAndLogin("flags-search");

        mockMvc.perform(get("/search")
                        .header(AUTHORIZATION, bearer(auth.accessToken()))
                        .param("q", "graph")
                        .param("limit", "20"))
                .andExpect(status().isNotFound());
    }

    @Test
    void graphExpandRejectsDepthAboveDefaultWhenFlagDisabled() throws Exception {
        AuthContext auth = registerAndLogin("flags-graph");
        String rootId = createRootPost(auth.accessToken(), "graph root");
        createReply(auth.accessToken(), rootId, "graph child");

        mockMvc.perform(get("/graphs/conversation/{rootId}", rootId)
                        .header(AUTHORIZATION, bearer(auth.accessToken()))
                        .param("depth", "2")
                        .param("limit", "120"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/graphs/conversation/{rootId}", rootId)
                        .header(AUTHORIZATION, bearer(auth.accessToken()))
                        .param("depth", "3")
                        .param("limit", "120"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Graph expand feature is disabled"));
    }

    private AuthContext registerAndLogin(String prefix) throws Exception {
        String email = prefix + "." + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
        String password = "StrongPassword123";

        mockMvc.perform(post("/auth/register")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "%s User",
                                  "email": "%s",
                                  "password": "%s"
                                }
                                """.formatted(prefix, email, password)))
                .andExpect(status().isCreated());

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
        String token = loginJson.get("accessToken").asText();
        assertTrue(!token.isBlank());
        return new AuthContext(token);
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

    private JsonNode readJson(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }

    private record AuthContext(String accessToken) {
    }
}
