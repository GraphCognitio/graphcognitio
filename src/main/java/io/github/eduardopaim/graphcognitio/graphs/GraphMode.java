package io.github.eduardopaim.graphcognitio.graphs;

import io.github.eduardopaim.graphcognitio.exception.BadRequestException;
import java.util.Locale;

public enum GraphMode {
    CONVERSATION,
    TOPIC,
    HYBRID;

    public String queryValue() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static GraphMode fromQueryParam(String value) {
        if (value == null || value.isBlank()) {
            return CONVERSATION;
        }

        String normalized = value.trim().toUpperCase(Locale.ROOT);
        try {
            return GraphMode.valueOf(normalized);
        } catch (IllegalArgumentException ex) {
            throw new BadRequestException("Invalid graph mode");
        }
    }
}
