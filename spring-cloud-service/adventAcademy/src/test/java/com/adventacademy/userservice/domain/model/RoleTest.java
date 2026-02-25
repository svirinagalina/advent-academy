package com.adventacademy.userservice.domain.model;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RoleTest {

    private Role role;
    private Permission permission;

    @BeforeEach
    void setUp() {
        role = new Role();
        role.setName("ADMIN");
        role.setDescription("Administrator role");

        permission = new Permission();
        permission.setName("USER_READ");
        permission.setDescription("Read user data");
    }

    @Test
    void addPermission_AddsPermissionToRole() {
        role.addPermission(permission);
        assertTrue(role.hasPermission("USER_READ"));
    }

    @Test
    void removePermission_RemovesPermissionFromRole() {
        role.addPermission(permission);
        assertTrue(role.hasPermission("USER_READ"));

        role.removePermission(permission);
        assertFalse(role.hasPermission("USER_READ"));
    }

    @Test
    void hasPermission_PermissionExists_ReturnsTrue() {
        role.addPermission(permission);
        assertTrue(role.hasPermission("USER_READ"));
    }

    @Test
    void hasPermission_PermissionDoesNotExist_ReturnsFalse() {
        assertFalse(role.hasPermission("USER_READ"));
    }

    @Test
    void canBeDeleted_SystemRole_ReturnsFalse() {
        role.setSystemRole(true);
        assertFalse(role.canBeDeleted());
    }

    @Test
    void canBeDeleted_NonSystemRole_ReturnsTrue() {
        role.setSystemRole(false);
        assertTrue(role.canBeDeleted());
    }
}
