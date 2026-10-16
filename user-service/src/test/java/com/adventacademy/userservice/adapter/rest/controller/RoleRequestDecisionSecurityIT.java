package com.adventacademy.userservice.adapter.rest.controller;

import com.adventacademy.userservice.application.port.AuditTrailPort;
import com.adventacademy.userservice.application.port.JwtProvider;
import com.adventacademy.userservice.application.port.RoleApprovalWorkflowPort;
import com.adventacademy.userservice.domain.model.RoleRequestStatus;
import com.adventacademy.userservice.domain.model.UserStatus;
import com.adventacademy.userservice.infrastructure.persistence.entity.RoleEntity;
import com.adventacademy.userservice.infrastructure.persistence.entity.RoleRequestEntity;
import com.adventacademy.userservice.infrastructure.persistence.entity.UserEntity;
import com.adventacademy.userservice.infrastructure.persistence.jpa.RoleJpaRepository;
import com.adventacademy.userservice.infrastructure.persistence.jpa.RoleRequestJpaRepository;
import com.adventacademy.userservice.infrastructure.persistence.jpa.UserJpaRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Exercises the auth boundary added around {@code POST /api/role-requests/{id}/decision}
 * (issue #34 review): anonymous/non-admin rejection, the reviewer coming from the
 * authenticated principal rather than the request body, and the self-approval guard.
 * <p>
 * Role requests here are seeded directly as already-decidable (PENDING with a
 * process instance key already attached) rather than through {@code submit()}, so
 * these tests stay independent of Camunda — {@link RoleApprovalWorkflowPort} is
 * mocked, since only the authorization layer is under test.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = "spring.flyway.enabled=false")
@Transactional
class RoleRequestDecisionSecurityIT {

    @Autowired private MockMvc mockMvc;
    @Autowired private JwtProvider jwtProvider;
    @Autowired private UserJpaRepository userJpaRepository;
    @Autowired private RoleJpaRepository roleJpaRepository;
    @Autowired private RoleRequestJpaRepository roleRequestJpaRepository;
    @MockitoBean private AuditTrailPort auditTrailPort;
    @MockitoBean private RoleApprovalWorkflowPort workflowPort;

    @Test
    void anonymousRequestIsRejectedWith401() throws Exception {
        UUID roleRequestId = seedDecidableRoleRequest(seedUser(), seedRole());

        mockMvc.perform(post("/api/role-requests/{id}/decision", roleRequestId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"APPROVE\"}"))
                .andExpect(status().isUnauthorized());

        verify(workflowPort, never()).submitDecision(anyLong(), any(), any(), any());
    }

    @Test
    void authenticatedNonAdminIsRejectedWith403() throws Exception {
        UUID nonAdminId = seedUser();
        String token = jwtProvider.generateAccessToken(nonAdminId, "user@test.com", Set.of());
        UUID roleRequestId = seedDecidableRoleRequest(seedUser(), seedRole());

        mockMvc.perform(post("/api/role-requests/{id}/decision", roleRequestId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"APPROVE\"}"))
                .andExpect(status().isForbidden());

        verify(workflowPort, never()).submitDecision(anyLong(), any(), any(), any());
    }

    @Test
    void invalidTokenIsRejectedWith401() throws Exception {
        UUID roleRequestId = seedDecidableRoleRequest(seedUser(), seedRole());

        mockMvc.perform(post("/api/role-requests/{id}/decision", roleRequestId)
                        .header("Authorization", "Bearer not-a-real-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"APPROVE\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void adminApprovalUsesAuthenticatedPrincipalAsReviewerIgnoringRequestBody() throws Exception {
        UUID adminId = seedAdminUser();
        String token = jwtProvider.generateAccessToken(adminId, "admin@test.com", Set.of("ADMIN"));
        UUID requesterId = seedUser();
        UUID roleRequestId = seedDecidableRoleRequest(requesterId, seedRole());
        UUID spoofedReviewerId = UUID.randomUUID();

        mockMvc.perform(post("/api/role-requests/{id}/decision", roleRequestId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        // reviewerId here must be ignored: it's not part of the DTO anymore,
                        // and Jackson silently drops unknown fields by default.
                        .content("{\"decision\":\"APPROVE\",\"reviewerId\":\"" + spoofedReviewerId + "\"}"))
                .andExpect(status().isAccepted());

        verify(workflowPort).submitDecision(anyLong(), eq("APPROVE"), eq(adminId), any());
    }

    @Test
    void selfApprovalIsRejectedWith403() throws Exception {
        UUID adminId = seedAdminUser();
        String token = jwtProvider.generateAccessToken(adminId, "admin@test.com", Set.of("ADMIN"));
        UUID roleRequestId = seedDecidableRoleRequest(adminId, seedRole());

        mockMvc.perform(post("/api/role-requests/{id}/decision", roleRequestId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"APPROVE\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("SELF_APPROVAL_NOT_ALLOWED"));

        verify(workflowPort, never()).submitDecision(anyLong(), any(), any(), any());
    }

    private UUID seedUser() {
        UserEntity user = new UserEntity();
        user.setEmail(UUID.randomUUID() + "@test.com");
        user.setPasswordHash("hash");
        user.setFirstName("Test");
        user.setLastName("User");
        user.setStatus(UserStatus.ACTIVE);
        user.setEmailVerified(true);
        return userJpaRepository.saveAndFlush(user).getId();
    }

    private UUID seedAdminUser() {
        RoleEntity adminRole = new RoleEntity();
        adminRole.setName("ADMIN");
        adminRole.setDescription("Administrator");
        RoleEntity savedAdminRole = roleJpaRepository.save(adminRole);

        UserEntity user = new UserEntity();
        user.setEmail(UUID.randomUUID() + "@test.com");
        user.setPasswordHash("hash");
        user.setFirstName("Admin");
        user.setLastName("User");
        user.setStatus(UserStatus.ACTIVE);
        user.setEmailVerified(true);
        user.setRoles(Set.of(savedAdminRole));
        return userJpaRepository.saveAndFlush(user).getId();
    }

    private UUID seedRole() {
        RoleEntity role = new RoleEntity();
        role.setName("ROLE_" + UUID.randomUUID().toString().replace("-", ""));
        role.setDescription("Requested role");
        return roleJpaRepository.save(role).getId();
    }

    private UUID seedDecidableRoleRequest(UUID requesterId, UUID requestedRoleId) {
        LocalDateTime now = LocalDateTime.now();
        RoleRequestEntity entity = new RoleRequestEntity();
        entity.setUserId(requesterId);
        entity.setRequestedRoleId(requestedRoleId);
        entity.setStatus(RoleRequestStatus.PENDING);
        entity.setProcessInstanceKey(1L);
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        return roleRequestJpaRepository.save(entity).getId();
    }
}
