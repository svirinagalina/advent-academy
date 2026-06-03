package com.adventacademy.userservice.infrastructure.persistence;

import com.adventacademy.userservice.domain.model.Role;
import com.adventacademy.userservice.domain.model.User;
import com.adventacademy.userservice.domain.model.UserStatus;
import com.adventacademy.userservice.domain.repository.UserRepository;
import com.adventacademy.userservice.infrastructure.persistence.entity.RoleEntity;
import com.adventacademy.userservice.infrastructure.persistence.entity.UserEntity;
import com.adventacademy.userservice.infrastructure.persistence.jpa.RoleJpaRepository;
import com.adventacademy.userservice.infrastructure.persistence.jpa.UserJpaRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the persistence contract for removing the last role from a user:
 * the join row in {@code user_roles} must actually be deleted, not silently
 * preserved by a {@code save()} that mistakes "roles not loaded" for
 * "roles loaded and intentionally emptied".
 */
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = "spring.flyway.enabled=false")
@Transactional
class UserRoleRemovalIT {

    @Autowired private UserRepository userRepository;
    @Autowired private UserJpaRepository userJpa;
    @Autowired private RoleJpaRepository roleJpa;
    @PersistenceContext private EntityManager em;

    @Test
    void removingTheLastRoleClearsTheJoinTable() {
        UUID userId = seedUserWithSingleRole();

        User user = userRepository.findByIdWithRoles(userId).orElseThrow();
        Role onlyRole = user.getRoles().iterator().next();
        user.removeRole(onlyRole);
        userRepository.save(user);

        em.flush();
        em.clear();

        User refetched = userRepository.findByIdWithRoles(userId).orElseThrow();
        assertThat(refetched.getRoles())
                .as("user_roles join row must be deleted after removing the only role")
                .isEmpty();
    }

    private UUID seedUserWithSingleRole() {
        RoleEntity role = new RoleEntity();
        role.setName("OnlyRole");
        role.setPermissions(new HashSet<>());
        roleJpa.save(role);

        UserEntity user = new UserEntity();
        user.setEmail("only-role@test.com");
        user.setPasswordHash("hash");
        user.setFirstName("X");
        user.setLastName("Y");
        user.setStatus(UserStatus.ACTIVE);
        user.setRoles(new HashSet<>(Set.of(role)));
        UserEntity persisted = userJpa.save(user);

        em.flush();
        em.clear();
        return persisted.getId();
    }
}
