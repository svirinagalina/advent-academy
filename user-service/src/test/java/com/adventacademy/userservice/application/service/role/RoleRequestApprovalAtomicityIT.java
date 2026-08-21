package com.adventacademy.userservice.application.service.role;

import com.adventacademy.userservice.application.port.AuditTrailPort;
import com.adventacademy.userservice.domain.model.RoleRequestStatus;
import com.adventacademy.userservice.domain.model.User;
import com.adventacademy.userservice.domain.model.UserStatus;
import com.adventacademy.userservice.domain.repository.UserRepository;
import com.adventacademy.userservice.infrastructure.persistence.entity.RoleEntity;
import com.adventacademy.userservice.infrastructure.persistence.entity.RoleRequestEntity;
import com.adventacademy.userservice.infrastructure.persistence.entity.UserEntity;
import com.adventacademy.userservice.infrastructure.persistence.jpa.RoleJpaRepository;
import com.adventacademy.userservice.infrastructure.persistence.jpa.RoleRequestJpaRepository;
import com.adventacademy.userservice.infrastructure.persistence.jpa.UserJpaRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

/**
 * Proves the atomicity fix for issue #34 review comment 2 against a real database
 * and a real transaction, not just mock call ordering: {@link RoleRequestService#approve}
 * must roll back the role grant too when the write that comes after it fails,
 * instead of leaving the user with a role for a request that ends up unresolved.
 * <p>
 * Deliberately not {@code @Transactional} at the class level — that would make
 * this test's own wrapping transaction the one that (never) commits, which
 * would prove nothing about {@code approve()}'s real commit/rollback behavior.
 * Every read below goes through its own fresh call, exactly like a genuinely
 * separate observer would see it.
 */
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = "spring.flyway.enabled=false")
class RoleRequestApprovalAtomicityIT {

    @Autowired private RoleRequestService roleRequestService;
    @Autowired private UserRepository userRepository;
    @Autowired private UserJpaRepository userJpaRepository;
    @Autowired private RoleJpaRepository roleJpaRepository;
    @Autowired private RoleRequestJpaRepository roleRequestJpaRepository;
    @MockitoBean private AuditTrailPort auditTrailPort;

    @Test
    void approveRollsBackTheRoleGrantWhenTheAuditWriteFails() {
        UUID userId = seedUser();
        UUID roleId = seedRole();
        UUID roleRequestId = seedPendingRoleRequest(userId, roleId);
        UUID reviewerId = seedUser();

        doThrow(new RuntimeException("audit backend down")).when(auditTrailPort).record(any());

        assertThatThrownBy(() -> roleRequestService.approve(roleRequestId, userId, roleId, reviewerId))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("audit backend down");

        User user = userRepository.findByIdWithRoles(userId).orElseThrow();
        assertThat(user.getRoles()).isEmpty();

        RoleRequestEntity request = roleRequestJpaRepository.findById(roleRequestId).orElseThrow();
        assertThat(request.getStatus()).isEqualTo(RoleRequestStatus.PENDING);
        assertThat(request.getReviewerId()).isNull();
    }

    private UUID seedUser() {
        UserEntity user = new UserEntity();
        user.setEmail(UUID.randomUUID() + "@test.com");
        user.setPasswordHash("hash");
        user.setFirstName("Atomicity");
        user.setLastName("Test");
        user.setStatus(UserStatus.ACTIVE);
        return userJpaRepository.save(user).getId();
    }

    private UUID seedRole() {
        RoleEntity role = new RoleEntity();
        role.setName("ROLE_" + UUID.randomUUID().toString().replace("-", ""));
        role.setDescription("Atomicity test role");
        return roleJpaRepository.save(role).getId();
    }

    private UUID seedPendingRoleRequest(UUID userId, UUID requestedRoleId) {
        LocalDateTime now = LocalDateTime.now();
        RoleRequestEntity entity = new RoleRequestEntity();
        entity.setUserId(userId);
        entity.setRequestedRoleId(requestedRoleId);
        entity.setStatus(RoleRequestStatus.PENDING);
        entity.setProcessInstanceKey(1L);
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        return roleRequestJpaRepository.save(entity).getId();
    }
}
