package io.github.eduardopaim.graphcognitio.security;

import io.github.eduardopaim.graphcognitio.entity.UserRole;
import io.github.eduardopaim.graphcognitio.exception.UnauthorizedException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class JwtService {

    private final JwtProperties jwtProperties;

    public JwtService(JwtProperties jwtProperties) {
        this.jwtProperties = jwtProperties;
    }

    public String generateAccessToken(UUID userId, String email, UserRole role) {
        Instant now = Instant.now();
        Instant expiresAt = now.plus(jwtProperties.getExpMinutes(), ChronoUnit.MINUTES);

        return Jwts.builder()
                .subject(userId.toString())
                .claim("email", email)
                .claim("role", role.name())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiresAt))
                .signWith(secretKey())
                .compact();
    }

    public AuthenticatedUser extractAuthenticatedUser(String token) {
        try {
            JwtParser parser = Jwts.parser()
                    .verifyWith(secretKey())
                    .build();
            Claims claims = parser.parseSignedClaims(token).getPayload();

            UUID userId = UUID.fromString(claims.getSubject());
            String roleClaim = claims.get("role", String.class);
            UserRole role = parseRole(roleClaim);
            return new AuthenticatedUser(userId, role);
        } catch (IllegalArgumentException | JwtException e) {
            throw new UnauthorizedException("Invalid or expired token");
        }
    }

    private UserRole parseRole(String roleClaim) {
        if (!StringUtils.hasText(roleClaim)) {
            return UserRole.USER;
        }
        try {
            return UserRole.valueOf(roleClaim);
        } catch (IllegalArgumentException ex) {
            return UserRole.USER;
        }
    }

    private SecretKey secretKey() {
        if (!StringUtils.hasText(jwtProperties.getSecret())) {
            throw new IllegalStateException("app.jwt.secret must be configured");
        }
        return Keys.hmacShaKeyFor(jwtProperties.getSecret().getBytes(StandardCharsets.UTF_8));
    }
}
