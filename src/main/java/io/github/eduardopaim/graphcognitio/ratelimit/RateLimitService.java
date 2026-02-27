package io.github.eduardopaim.graphcognitio.ratelimit;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;
import org.springframework.stereotype.Service;

@Service
public class RateLimitService {

    private static final long STALE_ENTRY_TTL_SECONDS = 3600;
    private static final long CLEANUP_EVERY_REQUESTS = 256;

    private final Map<String, WindowCounter> counters = new ConcurrentHashMap<>();
    private final AtomicLong requestCount = new AtomicLong();
    private final LongSupplier epochSecondSupplier;

    public RateLimitService() {
        this(() -> Instant.now().getEpochSecond());
    }

    RateLimitService(LongSupplier epochSecondSupplier) {
        this.epochSecondSupplier = epochSecondSupplier;
    }

    public RateLimitDecision consume(String key, int limit, int windowSeconds) {
        if (limit <= 0 || windowSeconds <= 0) {
            return new RateLimitDecision(true, limit, Integer.MAX_VALUE, 0, 0);
        }

        long now = epochSecondSupplier.getAsLong();
        long windowStart = now - (now % windowSeconds);
        long resetAt = windowStart + windowSeconds;

        WindowCounter counter = counters.computeIfAbsent(key, ignored -> new WindowCounter());
        RateLimitDecision decision = counter.consume(limit, windowStart, resetAt, now);

        if (requestCount.incrementAndGet() % CLEANUP_EVERY_REQUESTS == 0) {
            cleanup(now);
        }
        return decision;
    }

    private void cleanup(long now) {
        counters.entrySet().removeIf(entry -> now - entry.getValue().lastTouchedEpochSecond > STALE_ENTRY_TTL_SECONDS);
    }

    private static class WindowCounter {
        private long windowStartEpochSecond = -1;
        private int currentCount = 0;
        private long lastTouchedEpochSecond = 0;

        synchronized RateLimitDecision consume(int limit, long windowStart, long resetAt, long now) {
            if (windowStartEpochSecond != windowStart) {
                windowStartEpochSecond = windowStart;
                currentCount = 0;
            }

            if (currentCount >= limit) {
                long retryAfter = Math.max(0, resetAt - now);
                lastTouchedEpochSecond = now;
                return new RateLimitDecision(false, limit, 0, resetAt, retryAfter);
            }

            currentCount++;
            int remaining = Math.max(0, limit - currentCount);
            lastTouchedEpochSecond = now;
            return new RateLimitDecision(true, limit, remaining, resetAt, 0);
        }
    }
}
