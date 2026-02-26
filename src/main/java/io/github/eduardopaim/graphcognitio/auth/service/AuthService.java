package io.github.eduardopaim.graphcognitio.auth.service;

import io.github.eduardopaim.graphcognitio.auth.dto.AuthResponse;
import io.github.eduardopaim.graphcognitio.auth.dto.AuthUserResponse;
import io.github.eduardopaim.graphcognitio.auth.dto.LoginRequest;
import io.github.eduardopaim.graphcognitio.auth.dto.RegisterRequest;
import io.github.eduardopaim.graphcognitio.auth.mapper.AuthUserMapper;
import io.github.eduardopaim.graphcognitio.entity.UserEntity;
import io.github.eduardopaim.graphcognitio.exception.ConflictException;
import io.github.eduardopaim.graphcognitio.exception.UnauthorizedException;
import io.github.eduardopaim.graphcognitio.repository.UserRepository;
import io.github.eduardopaim.graphcognitio.security.JwtService;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthUserMapper authUserMapper;

    public AuthService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       JwtService jwtService,
                       AuthUserMapper authUserMapper) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.authUserMapper = authUserMapper;
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String normalizedEmail = normalizeEmail(request.email());

        if (userRepository.existsByEmail(normalizedEmail)) {
            throw new ConflictException("Email already registered");
        }

        UserEntity user = new UserEntity();
        user.setId(UUID.randomUUID());
        user.setName(request.name().trim());
        user.setEmail(normalizedEmail);
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setCreatedAt(Instant.now());

        UserEntity savedUser = userRepository.save(user);
        String accessToken = jwtService.generateAccessToken(savedUser.getId(), savedUser.getEmail());
        AuthUserResponse userResponse = authUserMapper.toResponse(savedUser);

        return new AuthResponse(accessToken, userResponse);
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        String normalizedEmail = normalizeEmail(request.email());

        UserEntity user = userRepository.findByEmail(normalizedEmail)
                .orElseThrow(() -> new UnauthorizedException("Invalid email or password"));

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new UnauthorizedException("Invalid email or password");
        }

        String accessToken = jwtService.generateAccessToken(user.getId(), user.getEmail());
        AuthUserResponse userResponse = authUserMapper.toResponse(user);

        return new AuthResponse(accessToken, userResponse);
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
