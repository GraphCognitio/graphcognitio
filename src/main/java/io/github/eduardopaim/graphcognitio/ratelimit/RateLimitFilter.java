package io.github.eduardopaim.graphcognitio.ratelimit;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.eduardopaim.graphcognitio.security.AuthenticatedUser;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Objects;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private final RateLimitService rateLimitService;
    private final RateLimitProperties rateLimitProperties;
    private final ObjectMapper objectMapper;

    public RateLimitFilter(RateLimitService rateLimitService,
                           RateLimitProperties rateLimitProperties,
                           ObjectMapper objectMapper) {
        this.rateLimitService = rateLimitService;
        this.rateLimitProperties = rateLimitProperties;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if (!rateLimitProperties.isEnabled()) {
            filterChain.doFilter(request, response);
            return;
        }

        RateLimitRuleSelection selection = selectRule(request);
        if (selection == null) {
            filterChain.doFilter(request, response);
            return;
        }

        String subject = resolveSubject(selection.strategy(), request);
        String key = selection.ruleKey() + ":" + subject;
        RateLimitDecision decision = rateLimitService.consume(
                key,
                selection.rule().getLimit(),
                selection.rule().getWindowSeconds()
        );

        applyRateLimitHeaders(response, decision);
        if (!decision.allowed()) {
            response.setHeader("Retry-After", Long.toString(decision.retryAfterSeconds()));
            writeTooManyRequests(response, selection.ruleKey());
            return;
        }

        filterChain.doFilter(request, response);
    }

    private void writeTooManyRequests(HttpServletResponse response, String ruleKey) throws IOException {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.TOO_MANY_REQUESTS,
                "Rate limit exceeded"
        );
        problemDetail.setTitle("Too Many Requests");
        problemDetail.setProperty("rule", ruleKey);

        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), problemDetail);
    }

    private void applyRateLimitHeaders(HttpServletResponse response, RateLimitDecision decision) {
        response.setHeader("X-RateLimit-Limit", Integer.toString(decision.limit()));
        response.setHeader("X-RateLimit-Remaining", Integer.toString(decision.remaining()));
        response.setHeader("X-RateLimit-Reset", Long.toString(decision.resetEpochSecond()));
    }

    private String resolveSubject(IdentityStrategy strategy, HttpServletRequest request) {
        if (strategy == IdentityStrategy.IP) {
            return "ip:" + extractClientIp(request);
        }

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof AuthenticatedUser authenticatedUser) {
            return "user:" + authenticatedUser.userId();
        }
        return "ip:" + extractClientIp(request);
    }

    private String extractClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return Objects.requireNonNullElse(request.getRemoteAddr(), "unknown");
    }

    private RateLimitRuleSelection selectRule(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String method = request.getMethod();

        if (HttpMethod.POST.matches(method) && "/auth/register".equals(uri)) {
            return new RateLimitRuleSelection("auth-register", rateLimitProperties.getAuthRegister(), IdentityStrategy.IP);
        }

        if (HttpMethod.POST.matches(method) && "/auth/login".equals(uri)) {
            return new RateLimitRuleSelection("auth-login", rateLimitProperties.getAuthLogin(), IdentityStrategy.IP);
        }

        if (HttpMethod.POST.matches(method) && "/posts".equals(uri)) {
            return new RateLimitRuleSelection("post-create", rateLimitProperties.getCreatePost(), IdentityStrategy.AUTH_USER);
        }

        if (HttpMethod.POST.matches(method) && uri.matches("^/posts/[0-9a-fA-F\\-]{36}/reply$")) {
            return new RateLimitRuleSelection("post-reply", rateLimitProperties.getCreateReply(), IdentityStrategy.AUTH_USER);
        }

        if ((HttpMethod.POST.matches(method) || HttpMethod.DELETE.matches(method))
                && uri.matches("^/posts/[0-9a-fA-F\\-]{36}/likes$")) {
            return new RateLimitRuleSelection("post-likes", rateLimitProperties.getLikes(), IdentityStrategy.AUTH_USER);
        }

        return null;
    }

    private enum IdentityStrategy {
        IP,
        AUTH_USER
    }

    private record RateLimitRuleSelection(String ruleKey, RateLimitProperties.Rule rule, IdentityStrategy strategy) {
    }
}
