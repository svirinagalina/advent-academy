package com.adventacademy.userservice.domain.model;

import com.adventacademy.userservice.domain.exception.InvalidUserStateException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class UserTest {

    private User user;

    @BeforeEach
    void setUp() {
        user = new User();
        user.setEmail("test@example.com");
        user.setPasswordHash("hashedPassword");
        user.setFirstName("John");
        user.setLastName("Doe");
        user.setStatus(UserStatus.ACTIVE);
    }

    @Test
    void canAuthenticate_ActiveUser_ReturnsTrue() {
        assertTrue(user.canAuthenticate());
    }

    @Test
    void canAuthenticate_BlockedUser_ReturnsFalse() {
        user.setStatus(UserStatus.BLOCKED);
        assertFalse(user.canAuthenticate());
    }

    @Test
    void canAuthenticate_DeletedUser_ReturnsFalse() {
        user.setStatus(UserStatus.DELETED);
        assertFalse(user.canAuthenticate());
    }

    @Test
    void canAuthenticate_SuspendedUser_ReturnsFalse() {
        user.setStatus(UserStatus.SUSPENDED);
        assertFalse(user.canAuthenticate());
    }

    @Test
    void canAuthenticate_LockedUser_ReturnsFalse() {
        user.setStatus(UserStatus.LOCKED);
        user.setLockedUntil(LocalDateTime.now().plusMinutes(30));
        assertFalse(user.canAuthenticate());
    }

    @Test
    void isLocked_LockedWithFutureDate_ReturnsTrue() {
        user.setStatus(UserStatus.LOCKED);
        user.setLockedUntil(LocalDateTime.now().plusMinutes(30));
        assertTrue(user.isLocked());
    }

    @Test
    void isLocked_LockedWithPastDate_ReturnsFalse() {
        user.setStatus(UserStatus.LOCKED);
        user.setLockedUntil(LocalDateTime.now().minusMinutes(30));
        assertFalse(user.isLocked());
    }

    @Test
    void incrementFailedAttempts_IncreasesCounter() {
        assertEquals(0, user.getFailedLoginAttempts());
        user.incrementFailedAttempts();
        assertEquals(1, user.getFailedLoginAttempts());
        user.incrementFailedAttempts();
        assertEquals(2, user.getFailedLoginAttempts());
    }

    @Test
    void resetFailedAttempts_SetsToZero() {
        user.incrementFailedAttempts();
        user.incrementFailedAttempts();
        assertEquals(2, user.getFailedLoginAttempts());
        user.resetFailedAttempts();
        assertEquals(0, user.getFailedLoginAttempts());
    }

    @Test
    void lock_SetsLockedStatus() {
        LocalDateTime lockUntil = LocalDateTime.now().plusMinutes(30);
        user.lock(lockUntil);
        assertEquals(UserStatus.LOCKED, user.getStatus());
        assertEquals(lockUntil, user.getLockedUntil());
    }

    @Test
    void unlock_SetsActiveStatus() {
        user.lock(LocalDateTime.now().plusMinutes(30));
        user.unlock();
        assertEquals(UserStatus.ACTIVE, user.getStatus());
        assertNull(user.getLockedUntil());
    }

    @Test
    void activate_PendingUser_SetsActive() {
        user.setStatus(UserStatus.PENDING);
        user.activate();
        assertEquals(UserStatus.ACTIVE, user.getStatus());
    }

    @Test
    void activate_DeletedUser_ThrowsException() {
        user.delete();
        assertThrows(InvalidUserStateException.class, () -> user.activate());
    }

    @Test
    void delete_SetsDeletedStatus() {
        user.delete();
        assertEquals(UserStatus.DELETED, user.getStatus());
    }

    @Test
    void addRole_DeletedUser_ThrowsException() {
        user.delete();
        Role role = new Role();
        role.setName("ADMIN");
        assertThrows(InvalidUserStateException.class, () -> user.addRole(role));
    }

    @Test
    void hasRole_RoleExists_ReturnsTrue() {
        Role adminRole = new Role();
        adminRole.setName("ADMIN");
        user.addRole(adminRole);
        assertTrue(user.hasRole("ADMIN"));
    }

    @Test
    void hasRole_RoleDoesNotExist_ReturnsFalse() {
        assertFalse(user.hasRole("ADMIN"));
    }

    @Test
    void hasPermission_PermissionExists_ReturnsTrue() {
        Permission permission = new Permission();
        permission.setName("USER_READ");

        Role role = new Role();
        role.setName("ADMIN");
        role.addPermission(permission);

        user.addRole(role);
        assertTrue(user.hasPermission("USER_READ"));
    }

    @Test
    void hasPermission_PermissionDoesNotExist_ReturnsFalse() {
        assertFalse(user.hasPermission("USER_READ"));
    }
}
