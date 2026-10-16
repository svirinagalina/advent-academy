package com.adventacademy.userservice.adapter.rest.controller;

import com.adventacademy.userservice.domain.model.UserStatus;
import com.adventacademy.userservice.infrastructure.persistence.entity.UserEntity;
import com.adventacademy.userservice.infrastructure.persistence.jpa.UserJpaRepository;
import com.sun.net.httpserver.HttpServer;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.test.web.servlet.MockMvc;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "spring.flyway.enabled=false")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class TeachingRosterControllerIT {
    private static final UUID COURSE = UUID.randomUUID();
    private static final HttpServer CONTENT = server();
    private static volatile int upstreamStatus = 200;
    private static volatile String forwarded;
    @Autowired MockMvc mvc;
    @Autowired UserJpaRepository users;
    @Autowired com.adventacademy.userservice.application.port.JwtProvider jwt;

    private static HttpServer server() {
        try {
            var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/api/teaching/courses/" + COURSE, exchange -> {
                forwarded = exchange.getRequestHeaders().getFirst("Authorization");
                exchange.sendResponseHeaders(upstreamStatus, -1);
                exchange.close();
            });
            server.start();
            return server;
        } catch (java.io.IOException e) { throw new java.io.UncheckedIOException(e); }
    }
    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("teaching.content-service-url", () -> "http://127.0.0.1:" + CONTENT.getAddress().getPort());
    }
    @AfterAll static void stop() { CONTENT.stop(0); }

    @Test
    void searchReturnsOnlyMinimalActiveVerifiedProfilesAndEscapesWildcards() throws Exception {
        upstreamStatus = 200;
        var active = seed("needle@example.test", UserStatus.ACTIVE, true);
        seed("needle-pending@example.test", UserStatus.PENDING, false);
        seed("needle-deleted@example.test", UserStatus.DELETED, true);
        seed("needle-unverified@example.test", UserStatus.ACTIVE, false);
        String bearer = token("INSTRUCTOR");
        mvc.perform(get(path()).param("query", "NEEDLE").header("Authorization", bearer))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(active.getId().toString()))
                .andExpect(jsonPath("$[0].email").value("needle@example.test"))
                .andExpect(jsonPath("$[0].passwordHash").doesNotExist())
                .andExpect(jsonPath("$[0].roles").doesNotExist());
        assertThat(forwarded).isEqualTo(bearer);
        mvc.perform(get(path()).param("query", "мария").param("limit", "1").header("Authorization", bearer))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1));
        mvc.perform(get(path()).header("Authorization", bearer)).andExpect(status().isBadRequest());
        mvc.perform(get(path()).param("query", "%_").header("Authorization", bearer))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
        mvc.perform(get(path()).param("query", "  ").header("Authorization", bearer)).andExpect(status().isBadRequest());
        mvc.perform(get(path()).param("query", "needle").param("limit", "51").header("Authorization", bearer))
                .andExpect(status().isBadRequest());
    }

    @Test
    void ownershipRoleAndDependencyFailuresDoNotDiscloseUsers() throws Exception {
        seed("denied@example.test", UserStatus.ACTIVE, true);
        mvc.perform(get(path()).param("query", "denied")).andExpect(status().isUnauthorized());
        mvc.perform(get(path()).param("query", "denied").header("Authorization", token("LEARNER")))
                .andExpect(status().isForbidden());
        upstreamStatus = 403;
        mvc.perform(get(path()).param("query", "denied").header("Authorization", token("INSTRUCTOR")))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$[0].email").doesNotExist());
        upstreamStatus = 500;
        mvc.perform(get(path()).param("query", "denied").header("Authorization", token("ADMIN")))
                .andExpect(status().isBadGateway());
    }

    @Test
    void lookupRejectsUnknownInactiveAndUnverifiedUsers() throws Exception {
        upstreamStatus = 200;
        var active = seed("active@example.test", UserStatus.ACTIVE, true);
        mvc.perform(get(path() + "/" + active.getId()).header("Authorization", token("ADMIN")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(active.getId().toString()));
        for (var state : UserStatus.values()) {
            if (state == UserStatus.ACTIVE) { continue; }
            var inactive = seed(state + "@example.test", state, true);
            mvc.perform(get(path() + "/" + inactive.getId()).header("Authorization", token("INSTRUCTOR")))
                    .andExpect(status().isUnprocessableEntity());
        }
        var unverified = seed("unverified@example.test", UserStatus.ACTIVE, false);
        for (UUID id : List.of(unverified.getId(), UUID.randomUUID())) {
            mvc.perform(get(path() + "/" + id).header("Authorization", token("INSTRUCTOR")))
                    .andExpect(status().isUnprocessableEntity());
        }
    }

    private UserEntity seed(String email, UserStatus state, boolean verified) {
        var user = new UserEntity();
        user.setId(UUID.randomUUID()); user.setEmail(email); user.setPasswordHash("test-hash");
        user.setFirstName("Мария"); user.setLastName("Проверка"); user.setStatus(state); user.setEmailVerified(verified);
        return users.saveAndFlush(user);
    }
    private String path() { return "/api/users/teaching/courses/" + COURSE + "/candidates"; }
    private String token(String role) {
        var actor = seed(UUID.randomUUID() + "@actor.test", UserStatus.ACTIVE, true);
        return "Bearer " + jwt.generateAccessToken(actor.getId(), actor.getEmail(), java.util.Set.of(role));
    }
}
