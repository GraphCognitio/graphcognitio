package io.github.eduardopaim.graphcognitio.posts.dto;

import io.github.eduardopaim.graphcognitio.exception.BadRequestException;
import java.util.Locale;

public enum FeedSort {
    RECENT,
    RELEVANT;

    public String queryValue() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static FeedSort fromQueryParam(String value) {
        if (value == null || value.isBlank()) {
            return RECENT;
        }

        String normalized = value.trim().toUpperCase(Locale.ROOT);
        try {
            return FeedSort.valueOf(normalized);
        } catch (IllegalArgumentException ex) {
            throw new BadRequestException("Invalid sort");
        }
    }
}
