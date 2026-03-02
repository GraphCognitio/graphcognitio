package io.github.eduardopaim.graphcognitio.embeddings;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.eduardopaim.graphcognitio.config.EmbeddingsProperties;
import org.junit.jupiter.api.Test;

class EmbeddingsHealthIndicatorTest {

    @Test
    void healthIsDownWhenOpenAiProviderHasNoApiKey() {
        EmbeddingsProperties properties = new EmbeddingsProperties();
        properties.setEnabled(true);
        properties.setProvider("openai");
        properties.setModel("text-embedding-3-small");
        properties.getRemote().setApiKey("");

        EmbeddingsHealthIndicator indicator = new EmbeddingsHealthIndicator(properties);

        assertThat(indicator.health().getStatus().getCode()).isEqualTo("DOWN");
        assertThat(indicator.health().getDetails()).containsEntry("reason", "APP_EMBEDDINGS_REMOTE_API_KEY is missing");
    }

    @Test
    void healthIsUpWhenHashingProviderIsEnabled() {
        EmbeddingsProperties properties = new EmbeddingsProperties();
        properties.setEnabled(true);
        properties.setProvider("hashing");

        EmbeddingsHealthIndicator indicator = new EmbeddingsHealthIndicator(properties);

        assertThat(indicator.health().getStatus().getCode()).isEqualTo("UP");
        assertThat(indicator.health().getDetails()).containsEntry("provider", "hashing");
    }
}
