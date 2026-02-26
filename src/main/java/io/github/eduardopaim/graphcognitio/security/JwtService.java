package io.github.eduardopaim.graphcognitio.security;

import io.github.eduardopaim.graphcognitio.exception.UnauthorizedException;
import io.jsonwebtoken.JwtException;
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

    public String generateAccessToken(UUID userId, String email) {
        Instant now = Instant.now();
        Instant expiresAt = now.plus(jwtProperties.getExpMinutes(), ChronoUnit.MINUTES);

        return Jwts.builder()
                .subject(userId.toString())
                .claim("email", email)
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiresAt))
                .signWith(secretKey())
                .compact();
    }

    public UUID extractUserId(String token) {
        try {
            String subject = Jwts.parser()
                    .verifyWith(secretKey())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload()
                    .getSubject();
            return UUID.fromString(subject);
        } catch (IllegalArgumentException | JwtException e) {
            throw new UnauthorizedException("Invalid or expired token");
        }
    }

    private SecretKey secretKey() {
        if (!StringUtils.hasText(jwtProperties.getSecret())) {
            throw new IllegalStateException("app.jwt.secret must be configured");
        }
        return Keys.hmacShaKeyFor(jwtProperties.getSecret().getBytes(StandardCharsets.UTF_8));
    }
}
