package com.joselorza.app.application.service;

import com.joselorza.app.application.event.PasswordResetRequestedEvent;
import com.joselorza.app.domain.exception.*;
import com.joselorza.app.infrastructure.repository.PasswordResetTokenRepository;
import com.joselorza.app.infrastructure.repository.RefreshTokenRepository;
import com.joselorza.app.infrastructure.repository.UserRepository;
import com.joselorza.app.domain.model.*;
import com.joselorza.app.domain.model.enumerated.Role;
import com.joselorza.app.infrastructure.security.JwtProperties;
import com.joselorza.app.infrastructure.security.JwtTokenProvider;
import com.joselorza.app.infrastructure.security.TokenHasher;
import com.joselorza.app.presentation.dto.request.ChangePasswordRequest;
import com.joselorza.app.presentation.dto.request.ForgotPasswordRequest;
import com.joselorza.app.presentation.dto.request.LoginRequest;
import com.joselorza.app.presentation.dto.request.RefreshRequest;
import com.joselorza.app.presentation.dto.request.RegisterRequest;
import com.joselorza.app.presentation.dto.request.ResetPasswordRequest;
import com.joselorza.app.presentation.dto.response.AuthResponse;
import com.joselorza.app.presentation.dto.response.UserResponse;
import com.joselorza.app.presentation.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class AuthService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final JwtProperties jwtProperties;
    private final UserMapper userMapper;
    private final ApplicationEventPublisher eventPublisher;
    private final SecureRandom secureRandom = new SecureRandom();

    public UserResponse register(RegisterRequest request) {
        if (!Boolean.TRUE.equals(request.acceptTerms())) {
            throw new BusinessRuleException("You must accept the terms of use and the privacy policy");
        }

        if (userRepository.existsByEmail(request.email())) {
            throw new ResourceAlreadyExistsException();
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
                .termsAcceptedAt(Instant.now()) // Evidencia de aceptación (Ley 1581 / GDPR)
                .build();

        return userMapper.toResponse(userRepository.save(user));
    }

    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.email())
                .orElseThrow(ResourceInvalidCredentialsException::new);

        if (!user.isActive() || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new ResourceInvalidCredentialsException();
        }

        return buildAuthResponse(user);
    }

    public AuthResponse refresh(RefreshRequest request) {
        String tokenHash = TokenHasher.hash(request.refreshToken());
        RefreshToken storedToken = refreshTokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(ResourceInvalidTokenException::new);

        if (storedToken.isRevoked() || storedToken.getExpiresAt().isBefore(Instant.now())
                || !storedToken.getUser().isActive()) {
            throw new ResourceInvalidTokenException();
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

    /**
     * Inicia la recuperación de contraseña. Siempre responde igual exista o no
     * el email, para no revelar qué cuentas están registradas.
     */
    public void forgotPassword(ForgotPasswordRequest request) {
        userRepository.findByEmail(request.email())
                .filter(User::isActive)
                .ifPresent(user -> {
                    passwordResetTokenRepository.invalidateAllByUserId(user.getId());

                    String rawToken = generateRawToken();
                    passwordResetTokenRepository.save(PasswordResetToken.builder()
                            .user(user)
                            .tokenHash(TokenHasher.hash(rawToken))
                            .expiresAt(Instant.now().plus(jwtProperties.getPasswordResetExpirationMinutes(), ChronoUnit.MINUTES))
                            .used(false)
                            .build());

                    // El correo se envía tras el commit, de forma asíncrona
                    eventPublisher.publishEvent(new PasswordResetRequestedEvent(user.getId(), rawToken));
                });
    }

    public void resetPassword(ResetPasswordRequest request) {
        PasswordResetToken token = passwordResetTokenRepository.findByTokenHash(TokenHasher.hash(request.token()))
                .orElseThrow(ResourceInvalidTokenException::new);

        if (token.isUsed() || token.getExpiresAt().isBefore(Instant.now()) || !token.getUser().isActive()) {
            throw new ResourceInvalidTokenException();
        }

        User user = token.getUser();
        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        userRepository.save(user);

        token.setUsed(true);
        passwordResetTokenRepository.save(token);

        // Cierra todas las sesiones abiertas con la contraseña anterior
        refreshTokenRepository.revokeAllByUserId(user.getId());
    }

    public void changePassword(UUID userId, ChangePasswordRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException());

        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new BusinessRuleException("The current password is incorrect");
        }

        if (passwordEncoder.matches(request.newPassword(), user.getPasswordHash())) {
            throw new BusinessRuleException("The new password must be different from the current one");
        }

        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        userRepository.save(user);

        refreshTokenRepository.revokeAllByUserId(user.getId());
    }

    private AuthResponse buildAuthResponse(User user) {
        String accessToken = jwtTokenProvider.generateAccessToken(user);
        String rawRefreshToken = generateRawToken();

        RefreshToken refreshToken = RefreshToken.builder()
                .user(user)
                .tokenHash(TokenHasher.hash(rawRefreshToken))
                .expiresAt(Instant.now().plus(jwtProperties.getRefreshExpirationDays(), ChronoUnit.DAYS))
                .revoked(false)
                .build();
        refreshTokenRepository.save(refreshToken);

        long expiresIn = jwtProperties.getAccessExpirationMinutes() * 60L;
        return new AuthResponse(accessToken, rawRefreshToken, expiresIn, userMapper.toResponse(user));
    }

    private String generateRawToken() {
        byte[] randomBytes = new byte[64];
        secureRandom.nextBytes(randomBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
    }
}
