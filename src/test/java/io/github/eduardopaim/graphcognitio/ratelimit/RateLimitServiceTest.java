package io.github.eduardopaim.graphcognitio.ratelimit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

class RateLimitServiceTest {

    @Test
    void consumesWithinWindowAndBlocksWhenLimitExceeded() {
        AtomicLong now = new AtomicLong(1_000);
        RateLimitService service = new RateLimitService(now::get);

        RateLimitDecision first = service.consume("key-1", 2, 60);
        RateLimitDecision second = service.consume("key-1", 2, 60);
        RateLimitDecision third = service.consume("key-1", 2, 60);

        assertTrue(first.allowed());
        assertEquals(1, first.remaining());
        assertTrue(second.allowed());
        assertEquals(0, second.remaining());
        assertFalse(third.allowed());
        assertEquals(0, third.remaining());
        assertTrue(third.retryAfterSeconds() > 0);
    }

    @Test
    void resetsCounterOnNextWindow() {
        AtomicLong now = new AtomicLong(1_000);
        RateLimitService service = new RateLimitService(now::get);

        RateLimitDecision first = service.consume("key-2", 1, 10);
        RateLimitDecision blocked = service.consume("key-2", 1, 10);
        now.set(1_011);
        RateLimitDecision afterReset = service.consume("key-2", 1, 10);

        assertTrue(first.allowed());
        assertFalse(blocked.allowed());
        assertTrue(afterReset.allowed());
        assertEquals(0, afterReset.remaining());
    }
}
