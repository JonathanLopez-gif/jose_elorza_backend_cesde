package com.joselorza.app.application.service;

import com.joselorza.app.application.exception.*;
import com.joselorza.app.application.port.out.RefreshTokenRepository;
import com.joselorza.app.application.port.out.UserRepository;
import com.joselorza.app.domain.model.*;
import com.joselorza.app.infrastructure.security.JwtProperties;
import com.joselorza.app.infrastructure.security.JwtTokenProvider;
import com.joselorza.app.infrastructure.security.TokenHasher;
import com.joselorza.app.presentation.dto.request.LoginRequest;
import com.joselorza.app.presentation.dto.request.RefreshRequest;
import com.joselorza.app.presentation.dto.request.RegisterRequest;
import com.joselorza.app.presentation.dto.response.AuthResponse;
import com.joselorza.app.presentation.dto.response.UserResponse;
import com.joselorza.app.presentation.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;

@Service
@RequiredArgsConstructor
@Transactional
public class AuthService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final JwtProperties jwtProperties;
    private final UserMapper userMapper;
    private final SecureRandom secureRandom = new SecureRandom();

    public UserResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new EmailAlreadyExistsException(request.email());
        }

        User user = User.builder()
                .name(request.name())
                .email(request.email())
                .passwordHash(passwordEncoder.encode(request.password()))
                .role(Role.USER) // Forzado: el registro público nunca crea ADMIN
                .timezone(request.timezone())
                .phone(request.phone())
                .notifyEmail(true)
                .isActive(true)
                .build();

        return userMapper.toResponse(userRepository.save(user));
    }

    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.email())
                .orElseThrow(InvalidCredentialsException::new);

        if (!user.isActive() || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new InvalidCredentialsException();
        }

        return buildAuthResponse(user);
    }

    public AuthResponse refresh(RefreshRequest request) {
        String tokenHash = TokenHasher.hash(request.refreshToken());
        RefreshToken storedToken = refreshTokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(InvalidRefreshTokenException::new);

        if (storedToken.isRevoked() || storedToken.getExpiresAt().isBefore(Instant.now())) {
            throw new InvalidRefreshTokenException();
        }

        // Rotación: se revoca el token actual antes de emitir uno nuevo
        storedToken.setRevoked(true);
        refreshTokenRepository.save(storedToken);

        return buildAuthResponse(storedToken.getUser());
    }

    public void logout(String rawRefreshToken) {
        String tokenHash = TokenHasher.hash(rawRefreshToken);
        refreshTokenRepository.findByTokenHash(tokenHash)
                .ifPresent(token -> {
                    token.setRevoked(true);
                    refreshTokenRepository.save(token);
                });
    }

    private AuthResponse buildAuthResponse(User user) {
        String accessToken = jwtTokenProvider.generateAccessToken(user);
        String rawRefreshToken = generateRawRefreshToken();

        RefreshToken refreshToken = RefreshToken.builder()
                .user(user)
                .tokenHash(TokenHasher.hash(rawRefreshToken))
                .expiresAt(Instant.now().plus(jwtProperties.getRefreshExpirationDays(), ChronoUnit.DAYS))
                .revoked(false)
                .build();
        refreshTokenRepository.save(refreshToken);

        long expiresIn = jwtProperties.getAccessExpirationMinutes() * 60L;
        return new AuthResponse(accessToken, rawRefreshToken, expiresIn);
    }

    private String generateRawRefreshToken() {
        byte[] randomBytes = new byte[64];
        secureRandom.nextBytes(randomBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
    }
}