package io.github.eduardopaim.graphcognitio.observability;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class HttpMetricsFilterTest {

    @Test
    void recordsLatencyAndErrorCounterForServerError() throws Exception {
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        HttpMetricsFilter filter = new HttpMetricsFilter(meterRegistry);

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/posts/123e4567-e89b-12d3-a456-426614174000/reply");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain filterChain = new MockFilterChain(new HttpServlet() {
            @Override
            protected void service(HttpServletRequest req, HttpServletResponse res) throws IOException {
                res.setStatus(500);
            }
        });

        filter.doFilter(request, response, filterChain);

        Timer timer = meterRegistry.find("graphcognitio.http.server.latency")
                .tags("method", "POST", "uri", "/posts/{id}/reply", "status", "500")
                .timer();
        Counter errors = meterRegistry.find("graphcognitio.http.server.errors")
                .tags("method", "POST", "uri", "/posts/{id}/reply", "statusClass", "5xx")
                .counter();

        assertEquals(1L, timer.count());
        assertEquals(1.0d, errors.count());
    }

    @Test
    void recordsLatencyWithoutErrorCounterForSuccess() throws Exception {
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        HttpMetricsFilter filter = new HttpMetricsFilter(meterRegistry);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/feed");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain filterChain = new MockFilterChain(new HttpServlet() {
            @Override
            protected void service(HttpServletRequest req, HttpServletResponse res) throws IOException {
                res.setStatus(200);
            }
        });

        filter.doFilter(request, response, filterChain);

        Timer timer = meterRegistry.find("graphcognitio.http.server.latency")
                .tags("method", "GET", "uri", "/feed", "status", "200")
                .timer();
        Counter errors = meterRegistry.find("graphcognitio.http.server.errors")
                .tags("method", "GET", "uri", "/feed", "statusClass", "4xx")
                .counter();

        assertEquals(1L, timer.count());
        assertNull(errors);
    }
}
