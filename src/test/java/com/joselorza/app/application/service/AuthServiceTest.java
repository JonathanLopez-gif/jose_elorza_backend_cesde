package com.joselorza.app.application.service;

import com.joselorza.app.application.event.PasswordResetRequestedEvent;
import com.joselorza.app.domain.exception.BusinessRuleException;
import com.joselorza.app.domain.exception.ResourceAlreadyExistsException;
import com.joselorza.app.domain.exception.ResourceInvalidCredentialsException;
import com.joselorza.app.domain.exception.ResourceInvalidTokenException;
import com.joselorza.app.domain.model.PasswordResetToken;
import com.joselorza.app.domain.model.RefreshToken;
import com.joselorza.app.domain.model.User;
import com.joselorza.app.domain.model.enumerated.Role;
import com.joselorza.app.infrastructure.repository.PasswordResetTokenRepository;
import com.joselorza.app.infrastructure.repository.RefreshTokenRepository;
import com.joselorza.app.infrastructure.repository.UserRepository;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private RefreshTokenRepository refreshTokenRepository;
    @Mock private PasswordResetTokenRepository passwordResetTokenRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private JwtTokenProvider jwtTokenProvider;
    @Mock private ApplicationEventPublisher eventPublisher;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        JwtProperties jwtProperties = new JwtProperties();
        jwtProperties.setAccessExpirationMinutes(15);
        jwtProperties.setRefreshExpirationDays(7);
        jwtProperties.setPasswordResetExpirationMinutes(30);

        authService = new AuthService(userRepository, refreshTokenRepository, passwordResetTokenRepository,
                passwordEncoder, jwtTokenProvider, jwtProperties, new UserMapper(), eventPublisher);
    }

    @Test
    void register_withoutAcceptingTerms_throwsBusinessRule() {
        RegisterRequest request = registerRequest(false);

        assertThatThrownBy(() -> authService.register(request))
                .isInstanceOf(BusinessRuleException.class);
        verify(userRepository, never()).save(any());
    }

    @Test
    void register_withDuplicatedEmail_throwsAlreadyExists() {
        when(userRepository.existsByEmail("ana@test.com")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(registerRequest(true)))
                .isInstanceOf(ResourceAlreadyExistsException.class);
    }

    @Test
    void register_success_forcesUserRoleAndStoresTermsAcceptance() {
        when(userRepository.existsByEmail("ana@test.com")).thenReturn(false);
        when(passwordEncoder.encode("password123")).thenReturn("hashed");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        UserResponse response = authService.register(registerRequest(true));

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        User saved = captor.getValue();

        assertThat(saved.getRole()).isEqualTo(Role.USER);
        assertThat(saved.getPasswordHash()).isEqualTo("hashed");
        assertThat(saved.getTermsAcceptedAt()).isNotNull();
        assertThat(response.email()).isEqualTo("ana@test.com");
    }

    @Test
    void login_withInactiveUser_throwsInvalidCredentials() {
        User user = user();
        user.setActive(false);
        when(userRepository.findByEmail("ana@test.com")).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> authService.login(new LoginRequest("ana@test.com", "password123")))
                .isInstanceOf(ResourceInvalidCredentialsException.class);
    }

    @Test
    void login_withWrongPassword_throwsInvalidCredentials() {
        when(userRepository.findByEmail("ana@test.com")).thenReturn(Optional.of(user()));
        when(passwordEncoder.matches("wrong", "hashed")).thenReturn(false);

        assertThatThrownBy(() -> authService.login(new LoginRequest("ana@test.com", "wrong")))
                .isInstanceOf(ResourceInvalidCredentialsException.class);
    }

    @Test
    void login_success_returnsAccessAndRefreshTokens() {
        User user = user();
        when(userRepository.findByEmail("ana@test.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("password123", "hashed")).thenReturn(true);
        when(jwtTokenProvider.generateAccessToken(user)).thenReturn("access-token");

        AuthResponse response = authService.login(new LoginRequest("ana@test.com", "password123"));

        assertThat(response.accessToken()).isEqualTo("access-token");
        assertThat(response.refreshToken()).isNotBlank();
        assertThat(response.expiresIn()).isEqualTo(15 * 60L);

        // Solo se persiste el hash del refresh token
        ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokenRepository).save(captor.capture());
        assertThat(captor.getValue().getTokenHash()).isEqualTo(TokenHasher.hash(response.refreshToken()));
    }

    @Test
    void refresh_withRevokedToken_throwsInvalidToken() {
        RefreshToken token = RefreshToken.builder()
                .user(user())
                .tokenHash(TokenHasher.hash("raw"))
                .expiresAt(Instant.now().plusSeconds(3600))
                .revoked(true)
                .build();
        when(refreshTokenRepository.findByTokenHash(TokenHasher.hash("raw"))).thenReturn(Optional.of(token));

        assertThatThrownBy(() -> authService.refresh(new RefreshRequest("raw")))
                .isInstanceOf(ResourceInvalidTokenException.class);
    }

    @Test
    void forgotPassword_withUnknownEmail_doesNothing() {
        when(userRepository.findByEmail("nobody@test.com")).thenReturn(Optional.empty());

        authService.forgotPassword(new ForgotPasswordRequest("nobody@test.com"));

        verify(passwordResetTokenRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void forgotPassword_withExistingUser_storesHashedTokenAndPublishesEvent() {
        User user = user();
        when(userRepository.findByEmail("ana@test.com")).thenReturn(Optional.of(user));

        authService.forgotPassword(new ForgotPasswordRequest("ana@test.com"));

        ArgumentCaptor<PasswordResetToken> tokenCaptor = ArgumentCaptor.forClass(PasswordResetToken.class);
        verify(passwordResetTokenRepository).save(tokenCaptor.capture());
        ArgumentCaptor<PasswordResetRequestedEvent> eventCaptor = ArgumentCaptor.forClass(PasswordResetRequestedEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());

        verify(passwordResetTokenRepository).invalidateAllByUserId(user.getId());
        assertThat(tokenCaptor.getValue().getTokenHash())
                .isEqualTo(TokenHasher.hash(eventCaptor.getValue().rawToken()));
        assertThat(tokenCaptor.getValue().getExpiresAt()).isAfter(Instant.now().plusSeconds(29 * 60));
    }

    @Test
    void resetPassword_withExpiredToken_throwsInvalidToken() {
        PasswordResetToken token = resetToken(Instant.now().minusSeconds(1), false);
        when(passwordResetTokenRepository.findByTokenHash(TokenHasher.hash("raw"))).thenReturn(Optional.of(token));

        assertThatThrownBy(() -> authService.resetPassword(new ResetPasswordRequest("raw", "newPassword1")))
                .isInstanceOf(ResourceInvalidTokenException.class);
    }

    @Test
    void resetPassword_withUsedToken_throwsInvalidToken() {
        PasswordResetToken token = resetToken(Instant.now().plusSeconds(600), true);
        when(passwordResetTokenRepository.findByTokenHash(TokenHasher.hash("raw"))).thenReturn(Optional.of(token));

        assertThatThrownBy(() -> authService.resetPassword(new ResetPasswordRequest("raw", "newPassword1")))
                .isInstanceOf(ResourceInvalidTokenException.class);
    }

    @Test
    void resetPassword_success_updatesPasswordAndRevokesSessions() {
        PasswordResetToken token = resetToken(Instant.now().plusSeconds(600), false);
        when(passwordResetTokenRepository.findByTokenHash(TokenHasher.hash("raw"))).thenReturn(Optional.of(token));
        when(passwordEncoder.encode("newPassword1")).thenReturn("new-hash");

        authService.resetPassword(new ResetPasswordRequest("raw", "newPassword1"));

        assertThat(token.getUser().getPasswordHash()).isEqualTo("new-hash");
        assertThat(token.isUsed()).isTrue();
        verify(refreshTokenRepository).revokeAllByUserId(token.getUser().getId());
    }

    @Test
    void changePassword_withWrongCurrentPassword_throwsBusinessRule() {
        User user = user();
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(anyString(), anyString())).thenReturn(false);

        assertThatThrownBy(() -> authService.changePassword(user.getId(),
                new ChangePasswordRequest("wrong", "newPassword1")))
                .isInstanceOf(BusinessRuleException.class);
        verify(refreshTokenRepository, never()).revokeAllByUserId(any());
    }

    // --- helpers ---

    private RegisterRequest registerRequest(boolean acceptTerms) {
        return new RegisterRequest("Ana", "ana@test.com", "password123", "America/Bogota", null, acceptTerms);
    }

    private User user() {
        return User.builder()
                .id(UUID.randomUUID())
                .name("Ana")
                .email("ana@test.com")
                .passwordHash("hashed")
                .role(Role.USER)
                .timezone("America/Bogota")
                .isActive(true)
                .build();
    }

    private PasswordResetToken resetToken(Instant expiresAt, boolean used) {
        return PasswordResetToken.builder()
                .user(user())
                .tokenHash(TokenHasher.hash("raw"))
                .expiresAt(expiresAt)
                .used(used)
                .build();
    }
}
