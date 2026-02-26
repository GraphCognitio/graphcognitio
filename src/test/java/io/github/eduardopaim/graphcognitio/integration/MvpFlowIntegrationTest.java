package io.github.eduardopaim.graphcognitio.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.TimeZone;
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
@SpringBootTest
@AutoConfigureMockMvc
class MvpFlowIntegrationTest {

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
    void fullMvpFlowWorksWithJwtAndGraphFeatures() throws Exception {
        String email = "eduardo.integration@example.com";
        String password = "StrongPassword123";
        String registerPayload = """
                {
                  "name": "Eduardo Integration",
                  "email": "%s",
                  "password": "%s"
                }
                """.formatted(email, password);

        MvcResult registerResult = mockMvc.perform(post("/auth/register")
                        .contentType(APPLICATION_JSON)
                        .content(registerPayload))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode registerJson = readJson(registerResult);
        assertNotNull(registerJson.get("accessToken").asText());
        assertEquals(email, registerJson.get("user").get("email").asText());

        String loginPayload = """
                {
                  "email": "%s",
                  "password": "%s"
                }
                """.formatted(email, password);

        MvcResult loginResult = mockMvc.perform(post("/auth/login")
                        .contentType(APPLICATION_JSON)
                        .content(loginPayload))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode loginJson = readJson(loginResult);
        String accessToken = loginJson.get("accessToken").asText();
        assertNotNull(accessToken);
        assertTrue(!accessToken.isBlank());

        MvcResult createPostResult = mockMvc.perform(post("/posts")
                        .header(AUTHORIZATION, bearer(accessToken))
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "content": "Root post for integration flow"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andReturn();

        JsonNode rootPostJson = readJson(createPostResult);
        String rootId = rootPostJson.get("id").asText();
        assertEquals(rootId, rootPostJson.get("rootId").asText());
        assertTrue(rootPostJson.get("parentId").isNull());

        MvcResult replyResult = mockMvc.perform(post("/posts/{id}/reply", rootId)
                        .header(AUTHORIZATION, bearer(accessToken))
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "content": "Reply post for integration flow"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", org.hamcrest.Matchers.startsWith("/posts/")))
                .andReturn();

        JsonNode replyJson = readJson(replyResult);
        String replyId = replyJson.get("id").asText();
        assertEquals(rootId, replyJson.get("parentId").asText());
        assertEquals(rootId, replyJson.get("rootId").asText());

        MvcResult rootDetailResult = mockMvc.perform(get("/posts/{id}", rootId)
                        .header(AUTHORIZATION, bearer(accessToken)))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode rootDetailJson = readJson(rootDetailResult);
        assertEquals(1, rootDetailJson.get("replyCount").asInt());

        MvcResult feedResult = mockMvc.perform(get("/feed")
                        .header(AUTHORIZATION, bearer(accessToken))
                        .param("limit", "20"))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode feedJson = readJson(feedResult);
        assertEquals(1, feedJson.get("items").size());
        assertEquals(rootId, feedJson.get("items").get(0).get("id").asText());
        assertTrue(feedJson.get("nextCursor").isNull());

        MvcResult graphResult = mockMvc.perform(get("/graphs/conversation/{rootId}", rootId)
                        .header(AUTHORIZATION, bearer(accessToken))
                        .param("depth", "2")
                        .param("limit", "120"))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode graphJson = readJson(graphResult);
        assertEquals(2, graphJson.get("nodes").size());
        assertEquals(1, graphJson.get("edges").size());
        assertEquals(replyId, graphJson.get("edges").get(0).get("sourcePostId").asText());
        assertEquals(rootId, graphJson.get("edges").get(0).get("targetPostId").asText());
        assertEquals("REPLY", graphJson.get("edges").get(0).get("type").asText());
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }

    private JsonNode readJson(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
    }
}
