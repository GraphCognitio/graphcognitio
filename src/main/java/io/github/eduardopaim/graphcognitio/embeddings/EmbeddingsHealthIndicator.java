package io.github.eduardopaim.graphcognitio.embeddings;

import io.github.eduardopaim.graphcognitio.config.EmbeddingsProperties;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component("embeddings")
public class EmbeddingsHealthIndicator implements HealthIndicator {

    private final EmbeddingsProperties embeddingsProperties;

    public EmbeddingsHealthIndicator(EmbeddingsProperties embeddingsProperties) {
        this.embeddingsProperties = embeddingsProperties;
    }

    @Override
    public Health health() {
        Health.Builder builder = Health.up()
                .withDetail("enabled", embeddingsProperties.isEnabled())
                .withDetail("provider", embeddingsProperties.getProvider())
                .withDetail("model", embeddingsProperties.getModel())
                .withDetail("dimensions", embeddingsProperties.getDimensions());

        if (!embeddingsProperties.isEnabled()) {
            return Health.unknown()
                    .withDetail("enabled", false)
                    .withDetail("provider", embeddingsProperties.getProvider())
                    .withDetail("reason", "Embeddings disabled")
                    .build();
        }

        if ("openai".equalsIgnoreCase(embeddingsProperties.getProvider())) {
            boolean apiKeyConfigured = StringUtils.hasText(embeddingsProperties.getRemote().getApiKey());
            builder.withDetail("remoteBaseUrl", embeddingsProperties.getRemote().getBaseUrl());
            builder.withDetail("remotePath", embeddingsProperties.getRemote().getPath());
            builder.withDetail("apiKeyConfigured", apiKeyConfigured);
            if (!apiKeyConfigured) {
                return Health.down()
                        .withDetail("enabled", true)
                        .withDetail("provider", embeddingsProperties.getProvider())
                        .withDetail("reason", "APP_EMBEDDINGS_REMOTE_API_KEY is missing")
                        .build();
            }
        }

        return builder.build();
    }
}
