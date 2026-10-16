package com.adventacademy.userservice.infrastructure.security;

import com.adventacademy.userservice.application.port.JwtProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Set;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = "spring.flyway.enabled=false")
class SecurityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtProvider jwtProvider;

    @Autowired
    private com.adventacademy.userservice.infrastructure.persistence.jpa.UserJpaRepository users;

    @Test
    void protectsApiAndRejectsRefreshTokenAsAccessToken() throws Exception {
        final UUID userId = account();
        final String refreshToken = jwtProvider.generateRefreshToken(userId);

        mockMvc.perform(get("/api/users/{id}", userId))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/users/{id}", userId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + refreshToken))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void accessTokenBindsIdentityAndRoles() throws Exception {
        final UUID userId = account();
        final String userToken = jwtProvider.generateAccessToken(
                userId, "user@example.com", Set.of("STUDENT"));
        final String adminToken = jwtProvider.generateAccessToken(
                account(), "admin@example.com", Set.of("ADMIN"));

        mockMvc.perform(get("/api/users/{id}", userId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + userToken))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/roles")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + userToken))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/roles")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk());
    }

    private UUID account() {
        var user = new com.adventacademy.userservice.infrastructure.persistence.entity.UserEntity();
        user.setEmail(UUID.randomUUID() + "@example.test");
        user.setPasswordHash("unused");
        user.setFirstName("Security");
        user.setLastName("Fixture");
        user.setStatus(com.adventacademy.userservice.domain.model.UserStatus.ACTIVE);
        user.setEmailVerified(true);
        return users.saveAndFlush(user).getId();
    }
}
