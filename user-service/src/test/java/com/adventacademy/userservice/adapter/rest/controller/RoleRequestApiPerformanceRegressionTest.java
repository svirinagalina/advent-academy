package com.adventacademy.userservice.adapter.rest.controller;

import com.adventacademy.userservice.domain.model.RoleRequestStatus;
import com.adventacademy.userservice.domain.model.UserStatus;
import com.adventacademy.userservice.infrastructure.persistence.entity.RoleRequestEntity;
import com.adventacademy.userservice.infrastructure.persistence.entity.UserEntity;
import com.adventacademy.userservice.infrastructure.persistence.jpa.RoleRequestJpaRepository;
import com.adventacademy.userservice.infrastructure.persistence.jpa.UserJpaRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.security.test.context.support.WithMockUser;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Tag("performance-regression")
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "spring.flyway.enabled=false",
        "spring.jpa.properties.hibernate.generate_statistics=true"
})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@WithMockUser(roles = "ADMIN")
class RoleRequestApiPerformanceRegressionTest {

    private static final int TARGET_USER_REQUESTS = 1_100;
    private static final int OTHER_USER_REQUESTS = 150;
    private static final int PAGE_SIZE = 50;
    private static final long ADMIN_LIST_SQL_BUDGET = 2L;
    private static final long USER_LIST_SQL_BUDGET = 3L;
    private static final Duration ADMIN_LIST_LATENCY_BUDGET = Duration.ofMillis(350);
    private static final Duration USER_LIST_LATENCY_BUDGET = Duration.ofMillis(400);

    @Autowired private MockMvc mockMvc;
    @Autowired private UserJpaRepository userJpaRepository;
    @Autowired private RoleRequestJpaRepository roleRequestJpaRepository;
    @PersistenceContext private EntityManager em;

    private UUID targetUserId;

    @BeforeAll
    void seedRoleRequests() {
        roleRequestJpaRepository.deleteAll();
        userJpaRepository.deleteAll();

        UserEntity targetUser = userJpaRepository.save(user("role-request-target"));
        UserEntity otherUser = userJpaRepository.save(user("role-request-other"));
        targetUserId = targetUser.getId();

        LocalDateTime baseTime = LocalDateTime.parse("2026-07-22T10:00:00");
        var roleRequests = new ArrayList<RoleRequestEntity>(TARGET_USER_REQUESTS + OTHER_USER_REQUESTS);

        for (int i = 0; i < TARGET_USER_REQUESTS; i++) {
            roleRequests.add(roleRequest(targetUserId, baseTime.minusMinutes(i)));
        }
        for (int i = 0; i < OTHER_USER_REQUESTS; i++) {
            roleRequests.add(roleRequest(otherUser.getId(), baseTime.minusDays(1).minusMinutes(i)));
        }

        roleRequestJpaRepository.saveAll(roleRequests);

        assertThat(roleRequestJpaRepository.count())
                .as("seed should contain more than 1000 role_requests")
                .isGreaterThan(1_000L);
    }

    @Test
    void adminRoleRequestsListStaysWithinPaginationSqlAndLatencyBudgets() throws Exception {
        warmUpAdminList();
        Statistics stats = resetStats();

        mockMvc.perform(get("/api/role-requests")
                        .param("page", "1")
                        .param("size", String.valueOf(PAGE_SIZE))
                        .param("sort", "createdAt,desc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(PAGE_SIZE))
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.size").value(PAGE_SIZE))
                .andExpect(jsonPath("$.totalElements").value(TARGET_USER_REQUESTS + OTHER_USER_REQUESTS))
                .andExpect(jsonPath("$.hasNext").value(true))
                .andExpect(jsonPath("$.hasPrevious").value(true));
        assertStatementCountAtMost(stats, ADMIN_LIST_SQL_BUDGET, "GET /api/role-requests");

        Duration latency = measureAverageLatency(() -> mockMvc.perform(get("/api/role-requests")
                        .param("page", "1")
                        .param("size", String.valueOf(PAGE_SIZE))
                        .param("sort", "createdAt,desc"))
                .andExpect(status().isOk()));
        assertLatencyAtMost(latency, ADMIN_LIST_LATENCY_BUDGET, "GET /api/role-requests");
    }

    @Test
    void userRoleRequestsListStaysWithinPaginationSqlAndLatencyBudgets() throws Exception {
        warmUpUserList();
        Statistics stats = resetStats();

        mockMvc.perform(get("/api/users/{userId}/role-requests", targetUserId)
                        .param("page", "1")
                        .param("size", String.valueOf(PAGE_SIZE))
                        .param("sort", "createdAt,desc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(PAGE_SIZE))
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.size").value(PAGE_SIZE))
                .andExpect(jsonPath("$.totalElements").value(TARGET_USER_REQUESTS))
                .andExpect(jsonPath("$.hasNext").value(true))
                .andExpect(jsonPath("$.hasPrevious").value(true));
        assertStatementCountAtMost(stats, USER_LIST_SQL_BUDGET, "GET /api/users/{userId}/role-requests");

        Duration latency = measureAverageLatency(() -> mockMvc.perform(get("/api/users/{userId}/role-requests", targetUserId)
                        .param("page", "1")
                        .param("size", String.valueOf(PAGE_SIZE))
                        .param("sort", "createdAt,desc"))
                .andExpect(status().isOk()));
        assertLatencyAtMost(latency, USER_LIST_LATENCY_BUDGET, "GET /api/users/{userId}/role-requests");
    }

    private UserEntity user(String prefix) {
        UserEntity user = new UserEntity();
        user.setEmail(prefix + "-" + UUID.randomUUID() + "@test.com");
        user.setPasswordHash("hash");
        user.setFirstName("Role");
        user.setLastName("Requester");
        user.setStatus(UserStatus.ACTIVE);
        return user;
    }

    private RoleRequestEntity roleRequest(UUID userId, LocalDateTime createdAt) {
        RoleRequestEntity roleRequest = new RoleRequestEntity();
        roleRequest.setUserId(userId);
        roleRequest.setRequestedRoleId(UUID.randomUUID());
        roleRequest.setStatus(RoleRequestStatus.PENDING);
        roleRequest.setCreatedAt(createdAt);
        roleRequest.setUpdatedAt(createdAt);
        return roleRequest;
    }

    private void warmUpAdminList() throws Exception {
        mockMvc.perform(get("/api/role-requests")
                        .param("page", "0")
                        .param("size", String.valueOf(PAGE_SIZE))
                        .param("sort", "createdAt,desc"))
                .andExpect(status().isOk());
    }

    private void warmUpUserList() throws Exception {
        mockMvc.perform(get("/api/users/{userId}/role-requests", targetUserId)
                        .param("page", "0")
                        .param("size", String.valueOf(PAGE_SIZE))
                        .param("sort", "createdAt,desc"))
                .andExpect(status().isOk());
    }

    private Statistics resetStats() {
        Statistics stats = em.getEntityManagerFactory()
                .unwrap(SessionFactory.class)
                .getStatistics();
        stats.clear();
        return stats;
    }

    private Duration measureAverageLatency(EndpointCall endpointCall) throws Exception {
        long totalNanos = 0L;
        int attempts = 3;

        for (int i = 0; i < attempts; i++) {
            long startedAt = System.nanoTime();
            endpointCall.execute();
            totalNanos += System.nanoTime() - startedAt;
        }

        return Duration.ofNanos(totalNanos / attempts);
    }

    private static void assertStatementCountAtMost(Statistics stats, long maxStatements, String scenario) {
        long statements = stats.getPrepareStatementCount();
        assertThat(statements)
                .as(scenario + " should stay within SQL statement budget")
                .isLessThanOrEqualTo(maxStatements);
    }

    private static void assertLatencyAtMost(Duration latency, Duration maxLatency, String scenario) {
        assertThat(latency)
                .as(scenario + " average latency should stay within regression budget")
                .isLessThanOrEqualTo(maxLatency);
    }

    @FunctionalInterface
    private interface EndpointCall {
        void execute() throws Exception;
    }
}
