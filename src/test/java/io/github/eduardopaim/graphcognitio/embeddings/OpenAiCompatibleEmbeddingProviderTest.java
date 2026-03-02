package io.github.eduardopaim.graphcognitio.embeddings;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.eduardopaim.graphcognitio.config.EmbeddingsProperties;
import org.junit.jupiter.api.Test;

class OpenAiCompatibleEmbeddingProviderTest {

    @Test
    void constructorRejectsMissingApiKey() {
        EmbeddingsProperties properties = new EmbeddingsProperties();
        properties.setProvider("openai");
        properties.getRemote().setApiKey("");

        assertThatThrownBy(() -> new OpenAiCompatibleEmbeddingProvider(properties))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("APP_EMBEDDINGS_REMOTE_API_KEY is required when APP_EMBEDDINGS_PROVIDER=openai");
    }
}
