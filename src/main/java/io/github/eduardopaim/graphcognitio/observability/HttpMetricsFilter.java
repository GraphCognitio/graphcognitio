package io.github.eduardopaim.graphcognitio.observability;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerMapping;

@Component
public class HttpMetricsFilter extends OncePerRequestFilter {

    private final MeterRegistry meterRegistry;

    public HttpMetricsFilter(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        Timer.Sample sample = Timer.start(meterRegistry);
        try {
            filterChain.doFilter(request, response);
        } finally {
            String method = request.getMethod();
            int status = response.getStatus();
            String uriTag = resolveUriTag(request);
            String statusTag = Integer.toString(status);

            sample.stop(
                    Timer.builder("graphcognitio.http.server.latency")
                            .description("HTTP server latency by route")
                            .tag("method", method)
                            .tag("uri", uriTag)
                            .tag("status", statusTag)
                            .register(meterRegistry)
            );

            if (status >= 400) {
                String statusClass = (status / 100) + "xx";
                Counter.builder("graphcognitio.http.server.errors")
                        .description("HTTP error responses")
                        .tag("method", method)
                        .tag("uri", uriTag)
                        .tag("statusClass", statusClass)
                        .register(meterRegistry)
                        .increment();
            }
        }
    }

    private String resolveUriTag(HttpServletRequest request) {
        Object bestMatchingPattern = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
        if (bestMatchingPattern instanceof String pattern && !pattern.isBlank()) {
            return pattern;
        }

        String uri = request.getRequestURI();
        if (uri == null || uri.isBlank()) {
            return "unknown";
        }
        if (uri.matches("^/posts/[0-9a-fA-F\\-]{36}/reply$")) {
            return "/posts/{id}/reply";
        }
        if (uri.matches("^/posts/[0-9a-fA-F\\-]{36}/likes$")) {
            return "/posts/{id}/likes";
        }
        if (uri.matches("^/posts/[0-9a-fA-F\\-]{36}$")) {
            return "/posts/{id}";
        }
        if (uri.matches("^/graphs/conversation/[0-9a-fA-F\\-]{36}$")) {
            return "/graphs/conversation/{rootId}";
        }
        if (uri.matches("^/admin/users/[0-9a-fA-F\\-]{36}/role$")) {
            return "/admin/users/{id}/role";
        }
        return uri;
    }
}
