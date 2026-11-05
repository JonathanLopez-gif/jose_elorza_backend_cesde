package com.joselorza.app.presentation.controller;

import com.joselorza.app.application.service.AuthService;
import com.joselorza.app.domain.exception.ResourceInvalidTokenException;
import com.joselorza.app.infrastructure.security.RefreshTokenCookieFactory;
import com.joselorza.app.presentation.dto.request.ForgotPasswordRequest;
import com.joselorza.app.presentation.dto.request.LoginRequest;
import com.joselorza.app.presentation.dto.request.RefreshRequest;
import com.joselorza.app.presentation.dto.request.RegisterRequest;
import com.joselorza.app.presentation.dto.request.ResetPasswordRequest;
import com.joselorza.app.presentation.dto.response.AuthResponse;
import com.joselorza.app.presentation.dto.response.UserResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.Cookie;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final RefreshTokenCookieFactory cookieFactory;

    @PostMapping("/register")
    public ResponseEntity<UserResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.register(request));
    }

    // El refresh token se entrega en el cuerpo y además en una cookie httpOnly (frontend web)
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return withRefreshCookie(authService.login(request));
    }

    // Acepta el refresh token en el cuerpo (clientes API) o en la cookie httpOnly (frontend web)
    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(
            @RequestBody(required = false) RefreshRequest request, HttpServletRequest httpRequest) {
        String rawToken = resolveRefreshToken(request, httpRequest);
        if (rawToken == null) {
            throw new ResourceInvalidTokenException();
        }
        return withRefreshCookie(authService.refresh(new RefreshRequest(rawToken)));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @RequestBody(required = false) RefreshRequest request, HttpServletRequest httpRequest) {
        String rawToken = resolveRefreshToken(request, httpRequest);
        if (rawToken != null) {
            authService.logout(rawToken);
        }
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, cookieFactory.clear().toString())
                .build();
    }

    // Siempre responde 202, exista o no el email (no revela cuentas registradas)
    @PostMapping("/forgot-password")
    public ResponseEntity<Void> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        authService.forgotPassword(request);
        return ResponseEntity.accepted().build();
    }

    @PostMapping("/reset-password")
    public ResponseEntity<Void> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        authService.resetPassword(request);
        return ResponseEntity.noContent().build();
    }

    private ResponseEntity<AuthResponse> withRefreshCookie(AuthResponse response) {
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookieFactory.create(response.refreshToken()).toString())
                .body(response);
    }

    private String resolveRefreshToken(RefreshRequest request, HttpServletRequest httpRequest) {
        if (request != null && StringUtils.hasText(request.refreshToken())) {
            return request.refreshToken();
        }
        Cookie[] cookies = httpRequest.getCookies();
        if (cookies == null) {
            return null;
        }
        return Arrays.stream(cookies)
                .filter(cookie -> cookieFactory.cookieName().equals(cookie.getName()))
                .map(Cookie::getValue)
                .filter(StringUtils::hasText)
                .findFirst()
                .orElse(null);
    }
}
