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
 * Diagnostic test that proves the fix for the N+1 / overfetching problem on the
 * RBAC graph. Seeds 20 users x 3 roles x 5 permissions and runs the three hot read
 * paths (list users, list roles, login-style findByEmail), asserting the post-fix
 * SQL counts after switching to {@code FetchType.LAZY} plus targeted
 * {@code @EntityGraph} fetch plans.
 * <p>
 * Pre-fix numbers (captured on commit 8a997f8 with EAGER on both relationships):
 * <ul>
 *   <li>{@code GET /api/users}     — 21 statements (1 + 20 lazy joins per user)</li>
 *   <li>{@code GET /api/roles}     — 4 statements (1 + 3 lazy joins per role)</li>
 *   <li>{@code findByEmail} login  — 2 statements (1 user + 1 eager roles/perms join)</li>
 * </ul>
 */
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "spring.jpa.properties.hibernate.generate_statistics=true",
        "spring.flyway.enabled=false",
        "spring.jpa.show-sql=true",
        "spring.jpa.properties.hibernate.format_sql=true",
        "logging.level.org.hibernate.SQL=DEBUG"
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

        System.out.println();
        System.out.println("################################################################");
        System.out.println("# Seeded: " + USERS + " users, " + ROLES_PER_USER
                + " roles, " + (ROLES_PER_USER * PERMS_PER_ROLE) + " permissions");
        System.out.println("# Each user has all " + ROLES_PER_USER + " roles assigned");
        System.out.println("# Each role has " + PERMS_PER_ROLE + " permissions assigned");
        System.out.println("################################################################");
    }

    @Test
    void getAllUsers_currentBehaviour_executesManyQueries() {
        Statistics stats = stats();
        stats.clear();

        Object users = getUserService.getAllUsers(0, 20, "id,asc");

        long queryCount = stats.getPrepareStatementCount();
        printReport("GET /api/users  (getAllUsers)", queryCount, stats);

        // Was 21 before the fix (1 + 20 lazy joins on roles+permissions).
        // After fix: @EntityGraph("roles") + LAZY permissions => 1-2 statements.
        assertThat(queryCount).as("getAllUsers AFTER fix").isLessThanOrEqualTo(3L);
    }

    @Test
    void getAllRoles_currentBehaviour_executesManyQueries() {
        Statistics stats = stats();
        stats.clear();

        List<RoleResponse> roles = getRoleService.getAllRoles();

        long queryCount = stats.getPrepareStatementCount();
        printReport("GET /api/roles  (getAllRoles)", queryCount, stats);

        assertThat(roles).hasSize(ROLES_PER_USER);
        // Was 4 before the fix (1 + 3 lazy joins on permissions).
        // After fix: @EntityGraph("permissions") => 1-2 statements.
        assertThat(queryCount).as("getAllRoles AFTER fix").isLessThanOrEqualTo(2L);
    }

    @Test
    void findByEmail_loginPath_overfetchesRolesAndPermissions() {
        Statistics stats = stats();
        stats.clear();

        Optional<User> user = userRepository.findByEmail("user0@test.com");

        long queryCount = stats.getPrepareStatementCount();
        printReport("POST /api/auth/login  (findByEmail)", queryCount, stats);

        assertThat(user).isPresent();
        // Was 2 before the fix (1 user + 1 eager roles/permissions join).
        // After fix (LAZY + lazy-safe mapper, login path never touches roles) => 1.
        assertThat(queryCount).as("findByEmail AFTER fix").isEqualTo(1L);
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
