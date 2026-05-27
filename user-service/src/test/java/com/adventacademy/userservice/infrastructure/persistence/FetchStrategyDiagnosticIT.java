package com.adventacademy.userservice.infrastructure.persistence;

import com.adventacademy.userservice.application.dto.response.RoleResponse;
import com.adventacademy.userservice.application.dto.response.UserResponse;
import com.adventacademy.userservice.application.service.role.GetRoleService;
import com.adventacademy.userservice.application.service.user.GetUserService;
import com.adventacademy.userservice.domain.model.User;
import com.adventacademy.userservice.domain.model.UserStatus;
import com.adventacademy.userservice.domain.repository.UserRepository;
import com.adventacademy.userservice.infrastructure.persistence.entity.PermissionEntity;
import com.adventacademy.userservice.infrastructure.persistence.entity.RoleEntity;
import com.adventacademy.userservice.infrastructure.persistence.entity.UserEntity;
import com.adventacademy.userservice.infrastructure.persistence.jpa.PermissionJpaRepository;
import com.adventacademy.userservice.infrastructure.persistence.jpa.RoleJpaRepository;
import com.adventacademy.userservice.infrastructure.persistence.jpa.UserJpaRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Diagnostic test that proves the N+1 / overfetching problem caused by
 * {@code FetchType.EAGER} on {@code UserEntity.roles} and {@code RoleEntity.permissions}.
 * <p>
 * Seeds a representative graph (20 users x 3 roles x 5 permissions) and runs the
 * three hot read paths (list users, list roles, login-style findByEmail), reporting
 * the number of SQL statements executed by Hibernate for each. The pre-fix assertions
 * pin the current bad behaviour so the same test can be re-purposed to prove the fix
 * by tightening the numbers in the next step.
 * </p>
 */
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "spring.jpa.properties.hibernate.generate_statistics=true",
        "spring.flyway.enabled=false"
})
@Transactional
class FetchStrategyDiagnosticIT {

    private static final int USERS = 20;
    private static final int ROLES_PER_USER = 3;
    private static final int PERMS_PER_ROLE = 5;

    @Autowired private UserJpaRepository userJpa;
    @Autowired private RoleJpaRepository roleJpa;
    @Autowired private PermissionJpaRepository permissionJpa;

    @Autowired private GetUserService getUserService;
    @Autowired private GetRoleService getRoleService;
    @Autowired private UserRepository userRepository;

    @PersistenceContext private EntityManager em;

    @BeforeEach
    void seed() {
        List<PermissionEntity> perms = new ArrayList<>();
        for (int i = 0; i < ROLES_PER_USER * PERMS_PER_ROLE; i++) {
            PermissionEntity p = new PermissionEntity();
            p.setName("perm_" + i);
            perms.add(permissionJpa.save(p));
        }

        List<RoleEntity> roles = new ArrayList<>();
        for (int i = 0; i < ROLES_PER_USER; i++) {
            RoleEntity r = new RoleEntity();
            r.setName("role_" + i);
            r.setPermissions(new HashSet<>(perms.subList(i * PERMS_PER_ROLE, (i + 1) * PERMS_PER_ROLE)));
            roles.add(roleJpa.save(r));
        }

        for (int i = 0; i < USERS; i++) {
            UserEntity u = new UserEntity();
            u.setEmail("user" + i + "@test.com");
            u.setPasswordHash("hash");
            u.setFirstName("First" + i);
            u.setLastName("Last" + i);
            u.setStatus(UserStatus.ACTIVE);
            u.setRoles(new HashSet<>(roles));
            userJpa.save(u);
        }

        em.flush();
        em.clear();
    }

    @Test
    void getAllUsers_currentBehaviour_executesManyQueries() {
        Statistics stats = stats();
        stats.clear();

        List<UserResponse> users = getUserService.getAllUsers();

        long queryCount = stats.getPrepareStatementCount();
        printReport("GET /api/users  (getAllUsers)", queryCount, stats);

        assertThat(users).hasSize(USERS);
        // Pre-fix snapshot: 1 SELECT for users + 20 SELECTs (one per user) for roles+permissions join = 21.
        // After fix with @EntityGraph(roles), this should drop to ~2 statements.
        assertThat(queryCount).as("getAllUsers BEFORE fix").isBetween(20L, 25L);
    }

    @Test
    void getAllRoles_currentBehaviour_executesManyQueries() {
        Statistics stats = stats();
        stats.clear();

        List<RoleResponse> roles = getRoleService.getAllRoles();

        long queryCount = stats.getPrepareStatementCount();
        printReport("GET /api/roles  (getAllRoles)", queryCount, stats);

        assertThat(roles).hasSize(ROLES_PER_USER);
        // Pre-fix snapshot: 1 SELECT for roles + 3 SELECTs (one per role) for permissions = 4.
        // After fix with @EntityGraph(permissions), this should drop to ~2.
        assertThat(queryCount).as("getAllRoles BEFORE fix").isBetween(4L, 6L);
    }

    @Test
    void findByEmail_loginPath_overfetchesRolesAndPermissions() {
        Statistics stats = stats();
        stats.clear();

        Optional<User> user = userRepository.findByEmail("user0@test.com");

        long queryCount = stats.getPrepareStatementCount();
        printReport("POST /api/auth/login  (findByEmail)", queryCount, stats);

        assertThat(user).isPresent();
        // Pre-fix snapshot: 1 SELECT user + 1 SELECT roles+permissions (eager) = 2.
        // After fix (no fetch of roles for login), this should drop to 1.
        assertThat(queryCount).as("findByEmail BEFORE fix").isBetween(2L, 3L);
    }

    private Statistics stats() {
        return em.getEntityManagerFactory()
                .unwrap(SessionFactory.class)
                .getStatistics();
    }

    private static void printReport(String scenario, long queries, Statistics s) {
        System.out.println();
        System.out.println("================================================================");
        System.out.println("Scenario: " + scenario);
        System.out.println("  Prepared statements   : " + queries);
        System.out.println("  Entity loads          : " + s.getEntityLoadCount());
        System.out.println("  Collection fetches    : " + s.getCollectionFetchCount());
        System.out.println("================================================================");
    }
}
