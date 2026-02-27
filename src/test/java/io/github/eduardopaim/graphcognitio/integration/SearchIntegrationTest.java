package io.github.eduardopaim.graphcognitio.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;
import java.util.TimeZone;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
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
@ActiveProfiles("test")
class SearchIntegrationTest {

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
    void searchReturnsCursorAndRemainingResultsOnNextPage() throws Exception {
        AuthContext auth = registerAndLogin("cursor");

        String firstId = createRootPost(auth.accessToken(), "search-cursor alpha");
        String secondId = createRootPost(auth.accessToken(), "search-cursor beta");
        String thirdId = createRootPost(auth.accessToken(), "search-cursor gamma");

        JsonNode firstPage = search(auth.accessToken(), "search-cursor", null, 2);
        assertEquals(2, firstPage.get("items").size());
        assertNotNull(firstPage.get("nextCursor"));
        assertTrue(!firstPage.get("nextCursor").isNull());

        String nextCursor = firstPage.get("nextCursor").asText();
        JsonNode secondPage = search(auth.accessToken(), "search-cursor", nextCursor, 2);

        assertEquals(1, secondPage.get("items").size());
        assertTrue(secondPage.get("nextCursor").isNull());

        Set<String> ids = new HashSet<>();
        firstPage.get("items").forEach(item -> ids.add(item.get("id").asText()));
        secondPage.get("items").forEach(item -> ids.add(item.get("id").asText()));

        assertEquals(Set.of(firstId, secondId, thirdId), ids);
    }

    @Test
    void searchRanksLikedPostAheadOfUnlikedPostWithSameTerm() throws Exception {
        AuthContext author = registerAndLogin("author");
        AuthContext liker = registerAndLogin("liker");

        String likedPostId = createRootPost(author.accessToken(), "frutiger-bubble glass node");
        String newerPostId = createRootPost(author.accessToken(), "frutiger-bubble glass node");

        likePost(liker.accessToken(), likedPostId);

        JsonNode searchResponse = search(author.accessToken(), "frutiger-bubble", null, 10);

        assertEquals(2, searchResponse.get("items").size());
        assertEquals(likedPostId, searchResponse.get("items").get(0).get("id").asText());
        assertEquals(newerPostId, searchResponse.get("items").get(1).get("id").asText());
    }

    private AuthContext registerAndLogin(String prefix) throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String email = prefix + "." + suffix + "@example.com";
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
        return new AuthContext(loginJson.get("accessToken").asText());
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

    private void likePost(String accessToken, String postId) throws Exception {
        mockMvc.perform(post("/posts/{id}/likes", postId)
                        .header(AUTHORIZATION, bearer(accessToken)))
                .andExpect(status().isOk());
    }

    private JsonNode search(String accessToken, String query, String cursor, int limit) throws Exception {
        var requestBuilder = get("/search")
                .header(AUTHORIZATION, bearer(accessToken))
                .param("q", query)
                .param("limit", Integer.toString(limit));

        if (cursor != null) {
            requestBuilder = requestBuilder.param("cursor", cursor);
        }

        MvcResult result = mockMvc.perform(requestBuilder)
                .andExpect(status().isOk())
                .andReturn();

        return readJson(result);
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
