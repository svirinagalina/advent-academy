package com.adventacademy.userservice.infrastructure.persistence;

import com.adventacademy.userservice.domain.model.RoleRequest;
import com.adventacademy.userservice.domain.model.RoleRequestStatus;
import com.adventacademy.userservice.domain.pagination.PageRequest;
import com.adventacademy.userservice.domain.pagination.Sort;
import com.adventacademy.userservice.domain.repository.RoleRequestRepository;
import com.adventacademy.userservice.infrastructure.persistence.entity.RoleEntity;
import com.adventacademy.userservice.infrastructure.persistence.entity.UserEntity;
import com.adventacademy.userservice.infrastructure.persistence.jpa.RoleJpaRepository;
import com.adventacademy.userservice.infrastructure.persistence.jpa.UserJpaRepository;
import com.adventacademy.userservice.domain.model.UserStatus;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = "spring.flyway.enabled=false")
@Transactional
class RoleRequestRepositoryAdapterIT {

    @Autowired private RoleRequestRepository roleRequestRepository;
    @Autowired private UserJpaRepository userJpaRepository;
    @Autowired private RoleJpaRepository roleJpaRepository;
    @PersistenceContext private EntityManager em;

    @Test
    void savesAndFindsRoleRequest() {
        Seed seed = seedUserAndRole();
        LocalDateTime now = LocalDateTime.parse("2026-07-22T10:00:00");

        RoleRequest saved = roleRequestRepository.save(RoleRequest.create(seed.userId(), seed.roleId(), now));

        assertThat(saved.getId()).isNotNull();

        em.flush();
        em.clear();

        RoleRequest found = roleRequestRepository.findById(saved.getId()).orElseThrow();
        assertThat(found.getUserId()).isEqualTo(seed.userId());
        assertThat(found.getRequestedRoleId()).isEqualTo(seed.roleId());
        assertThat(found.getStatus()).isEqualTo(RoleRequestStatus.PENDING);
        assertThat(roleRequestRepository.existsActiveByUserIdAndRequestedRoleId(seed.userId(), seed.roleId()))
                .isTrue();
    }

    @Test
    void findsRequestsByUser() {
        Seed seed = seedUserAndRole();
        LocalDateTime now = LocalDateTime.parse("2026-07-22T10:00:00");
        RoleRequest saved = roleRequestRepository.save(RoleRequest.create(seed.userId(), seed.roleId(), now));

        var page = roleRequestRepository.findByUserId(
                seed.userId(),
                PageRequest.of(0, 20, Sort.by(Sort.Order.desc("createdAt"))));

        assertThat(page.items())
                .extracting(RoleRequest::getId)
                .contains(saved.getId());
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

    private record Seed(UUID userId, UUID roleId) {
    }
}
