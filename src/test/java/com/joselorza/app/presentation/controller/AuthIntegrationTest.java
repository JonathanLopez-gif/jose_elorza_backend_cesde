package com.joselorza.app.presentation.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.joselorza.app.TestcontainersConfiguration;
import com.joselorza.app.application.port.out.EmailSenderPort;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Pruebas de integraciÃ³n del mÃ³dulo Auth contra PostgreSQL real (Testcontainers).
 * Se omiten automÃ¡ticamente si no hay Docker disponible.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
@Testcontainers(disabledWithoutDocker = true)
class AuthIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @MockBean private EmailSenderPort emailSender; // Captura el correo de recuperaciÃ³n sin SMTP

    @Test
    void register_login_refresh_and_profile_flow() throws Exception {
        String email = uniqueEmail();
        register(email, true);

        JsonNode tokens = login(email, "password123");
        String accessToken = tokens.get("accessToken").asText();
        String refreshToken = tokens.get("refreshToken").asText();

        mockMvc.perform(get("/api/users/me").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.role").value("USER"))
                .andExpect(jsonPath("$.termsAcceptedAt").isNotEmpty());

        // RotaciÃ³n: el refresh token usado queda revocado
        mockMvc.perform(post("/api/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("refreshToken", refreshToken))))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("refreshToken", refreshToken))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void register_withoutAcceptingTerms_returns400() throws Exception {
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(json(registerBody(uniqueEmail(), false))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void register_duplicatedEmail_returns409() throws Exception {
        String email = uniqueEmail();
        register(email, true);

        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(json(registerBody(email, true))))
                .andExpect(status().isConflict());
    }

    @Test
    void protectedEndpoints_requireValidTokenAndRole() throws Exception {
        mockMvc.perform(get("/api/users/me")).andExpect(status().isUnauthorized());

        String email = uniqueEmail();
        register(email, true);
        String accessToken = login(email, "password123").get("accessToken").asText();

        // Un USER no puede acceder a endpoints ADMIN
        mockMvc.perform(get("/api/users").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/admin/dashboard").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void forgotAndResetPassword_flow() throws Exception {
        String email = uniqueEmail();
        register(email, true);

        mockMvc.perform(post("/api/auth/forgot-password").contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", email))))
                .andExpect(status().isAccepted());

        // El correo se envÃ­a de forma asÃ­ncrona tras el commit
        ArgumentCaptor<String> html = ArgumentCaptor.forClass(String.class);
        verify(emailSender, timeout(5000)).send(eq(email), anyString(), html.capture());

        Matcher matcher = Pattern.compile("token=([A-Za-z0-9_-]+)").matcher(html.getValue());
        assertThat(matcher.find()).isTrue();
        String resetToken = matcher.group(1);

        mockMvc.perform(post("/api/auth/reset-password").contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("token", resetToken, "newPassword", "newPassword456"))))
                .andExpect(status().isNoContent());

        // El token es de un solo uso
        mockMvc.perform(post("/api/auth/reset-password").contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("token", resetToken, "newPassword", "otherPassword789"))))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", email, "password", "password123"))))
                .andExpect(status().isUnauthorized());
        login(email, "newPassword456");
    }

    @Test
    void forgotPassword_withUnknownEmail_returns202() throws Exception {
        mockMvc.perform(post("/api/auth/forgot-password").contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", uniqueEmail()))))
                .andExpect(status().isAccepted());
    }

    @Test
    void changePassword_requiresCurrentPassword() throws Exception {
        String email = uniqueEmail();
        register(email, true);
        String accessToken = login(email, "password123").get("accessToken").asText();

        mockMvc.perform(patch("/api/users/me/password").header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("currentPassword", "wrong-password", "newPassword", "newPassword456"))))
                .andExpect(status().isBadRequest());

        mockMvc.perform(patch("/api/users/me/password").header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("currentPassword", "password123", "newPassword", "newPassword456"))))
                .andExpect(status().isNoContent());

        login(email, "newPassword456");
    }

    // --- helpers ---

    private void register(String email, boolean acceptTerms) throws Exception {
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(json(registerBody(email, acceptTerms))))
                .andExpect(status().isCreated());
    }

    private JsonNode login(String email, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", email, "password", password))))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private Map<String, Object> registerBody(String email, boolean acceptTerms) {
        return Map.of(
                "name", "Usuario Test",
                "email", email,
                "password", "password123",
                "timezone", "America/Bogota",
                "acceptTerms", acceptTerms);
    }

    private String uniqueEmail() {
        return "user-" + UUID.randomUUID() + "@test.com";
    }

    private String json(Object body) throws Exception {
        return objectMapper.writeValueAsString(body);
    }
}
