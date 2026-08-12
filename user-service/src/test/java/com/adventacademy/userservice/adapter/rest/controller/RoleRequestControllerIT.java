package com.adventacademy.userservice.adapter.rest.controller;

import com.adventacademy.userservice.application.port.AuditTrailPort;
import com.adventacademy.userservice.domain.model.UserStatus;
import com.adventacademy.userservice.infrastructure.persistence.entity.RoleEntity;
import com.adventacademy.userservice.infrastructure.persistence.entity.UserEntity;
import com.adventacademy.userservice.infrastructure.persistence.jpa.RoleJpaRepository;
import com.adventacademy.userservice.infrastructure.persistence.jpa.UserJpaRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "spring.flyway.enabled=false",
        "spring.jpa.properties.hibernate.generate_statistics=true"
})
@Transactional
@WithMockUser(roles = "ADMIN")
class RoleRequestControllerIT {

    @Autowired private MockMvc mockMvc;
    @Autowired private UserJpaRepository userJpaRepository;
    @Autowired private RoleJpaRepository roleJpaRepository;
    @MockitoBean private AuditTrailPort auditTrailPort;
    @PersistenceContext private EntityManager em;

    @Test
    void submitsRoleRequestAndRejectsDuplicateActiveRequest() throws Exception {
        Seed seed = seedUserAndRole();
        String body = "{\"requestedRoleId\":\"" + seed.roleId() + "\"}";

        mockMvc.perform(post("/api/users/{userId}/role-requests", seed.userId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.userId").value(seed.userId().toString()))
                .andExpect(jsonPath("$.requestedRoleId").value(seed.roleId().toString()))
                .andExpect(jsonPath("$.status").value("PENDING"));

        mockMvc.perform(post("/api/users/{userId}/role-requests", seed.userId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("DUPLICATE_ACTIVE_ROLE_REQUEST"));
    }

    @Test
    void listsRequestsForUserAndAdmin() throws Exception {
        Seed seed = seedUserAndRole();
        String body = "{\"requestedRoleId\":\"" + seed.roleId() + "\"}";
        mockMvc.perform(post("/api/users/{userId}/role-requests", seed.userId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/users/{userId}/role-requests", seed.userId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].userId").value(seed.userId().toString()));

        mockMvc.perform(get("/api/role-requests"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].requestedRoleId").value(seed.roleId().toString()));
    }

    @Test
    void submitRoleRequestStaysWithinSqlStatementBudget() throws Exception {
        Seed seed = seedUserAndRole();
        String body = "{\"requestedRoleId\":\"" + seed.roleId() + "\"}";
        Statistics stats = resetStats();

        mockMvc.perform(post("/api/users/{userId}/role-requests", seed.userId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated());
        em.flush();

        assertStatementCountAtMost(stats, 4L, "POST /api/users/{userId}/role-requests");
    }

    @Test
    void listUserRoleRequestsStaysWithinSqlStatementBudget() throws Exception {
        Seed seed = seedUserAndRole();
        submitAndClear(seed);
        Statistics stats = resetStats();

        mockMvc.perform(get("/api/users/{userId}/role-requests", seed.userId()))
                .andExpect(status().isOk());

        assertStatementCountAtMost(stats, 3L, "GET /api/users/{userId}/role-requests");
    }

    @Test
    void listAdminRoleRequestsStaysWithinSqlStatementBudget() throws Exception {
        Seed seed = seedUserAndRole();
        submitAndClear(seed);
        Statistics stats = resetStats();

        mockMvc.perform(get("/api/role-requests"))
                .andExpect(status().isOk());

        assertStatementCountAtMost(stats, 2L, "GET /api/role-requests");
    }

    private Seed seedUserAndRole() {
        UserEntity user = new UserEntity();
        user.setEmail(UUID.randomUUID() + "@test.com");
        user.setPasswordHash("hash");
        user.setFirstName("Role");
        user.setLastName("Requester");
        user.setStatus(UserStatus.ACTIVE);
        UserEntity persistedUser = userJpaRepository.save(user);

        RoleEntity role = new RoleEntity();
        role.setName("ROLE_" + UUID.randomUUID().toString().replace("-", ""));
        role.setDescription("Requested role");
        RoleEntity persistedRole = roleJpaRepository.save(role);

        em.flush();
        em.clear();
        return new Seed(persistedUser.getId(), persistedRole.getId());
    }

    private void submitAndClear(Seed seed) throws Exception {
        String body = "{\"requestedRoleId\":\"" + seed.roleId() + "\"}";
        mockMvc.perform(post("/api/users/{userId}/role-requests", seed.userId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated());
        em.flush();
        em.clear();
    }

    private Statistics resetStats() {
        Statistics stats = em.getEntityManagerFactory()
                .unwrap(SessionFactory.class)
                .getStatistics();
        stats.clear();
        return stats;
    }

    private static void assertStatementCountAtMost(Statistics stats, long maxStatements, String scenario) {
        long statements = stats.getPrepareStatementCount();
        assertThat(statements)
                .as(scenario + " should stay within SQL statement budget")
                .isLessThanOrEqualTo(maxStatements);
    }

    private record Seed(UUID userId, UUID roleId) {
    }
}
