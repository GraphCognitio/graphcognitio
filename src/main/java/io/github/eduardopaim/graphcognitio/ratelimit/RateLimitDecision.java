package io.github.eduardopaim.graphcognitio.ratelimit;

public record RateLimitDecision(
        boolean allowed,
        int limit,
        int remaining,
        long resetEpochSecond,
        long retryAfterSeconds
) {
}
