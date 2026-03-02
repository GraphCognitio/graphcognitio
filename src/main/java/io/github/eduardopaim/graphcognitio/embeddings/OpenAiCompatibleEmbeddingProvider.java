package io.github.eduardopaim.graphcognitio.embeddings;

import io.github.eduardopaim.graphcognitio.config.EmbeddingsProperties;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

@Component
@ConditionalOnProperty(prefix = "app.embeddings", name = "provider", havingValue = "openai")
public class OpenAiCompatibleEmbeddingProvider implements EmbeddingProvider {

    private final EmbeddingsProperties embeddingsProperties;
    private final RestClient restClient;

    public OpenAiCompatibleEmbeddingProvider(EmbeddingsProperties embeddingsProperties) {
        this.embeddingsProperties = embeddingsProperties;
        if (!StringUtils.hasText(embeddingsProperties.getRemote().getApiKey())) {
            throw new IllegalStateException("APP_EMBEDDINGS_REMOTE_API_KEY is required when APP_EMBEDDINGS_PROVIDER=openai");
        }
        this.restClient = RestClient.builder()
                .baseUrl(embeddingsProperties.getRemote().getBaseUrl())
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + embeddingsProperties.getRemote().getApiKey())
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    @Override
    public EmbeddingVector embed(String text) {
        OpenAiEmbeddingResponse response = restClient.post()
                .uri(embeddingsProperties.getRemote().getPath())
                .body(new OpenAiEmbeddingRequest(model(), text, dimensions()))
                .retrieve()
                .body(OpenAiEmbeddingResponse.class);

        if (response == null || response.data() == null || response.data().isEmpty()) {
            throw new IllegalStateException("Embedding provider returned no data");
        }

        List<Double> values = response.data().get(0).embedding();
        float[] vector = new float[values.size()];
        for (int index = 0; index < values.size(); index++) {
            vector[index] = values.get(index).floatValue();
        }
        return new EmbeddingVector(model(), vector.length, vector);
    }

    @Override
    public String model() {
        return embeddingsProperties.getModel();
    }

    @Override
    public int dimensions() {
        return embeddingsProperties.getDimensions();
    }

    private record OpenAiEmbeddingRequest(String model, String input, int dimensions) {
    }

    private record OpenAiEmbeddingResponse(List<OpenAiEmbeddingData> data) {
    }

    private record OpenAiEmbeddingData(List<Double> embedding) {
    }
}
