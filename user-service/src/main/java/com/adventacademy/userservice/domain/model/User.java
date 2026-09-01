package com.adventacademy.userservice.domain.model;

import com.adventacademy.userservice.domain.exception.InvalidUserStateException;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class User {
    private UUID id;
    private String email;
    private String passwordHash;
    private String firstName;
    private String lastName;
    private UserStatus status;
    private Set<Role> roles;
    private boolean emailVerified;
    private String emailVerificationToken;
    private boolean mfaEnabled;
    private String mfaSecret;
    private int failedLoginAttempts;
    private LocalDateTime lockedUntil;
    private LocalDateTime lastLoginAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public User() {
        this.id = UUID.randomUUID();
        this.roles = new HashSet<>();
        this.status = UserStatus.PENDING;
        this.emailVerified = false;
        this.mfaEnabled = false;
        this.failedLoginAttempts = 0;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    public User(UUID id, String email, String passwordHash, String firstName, String lastName,
                UserStatus status, Set<Role> roles, boolean emailVerified, String emailVerificationToken,
                boolean mfaEnabled, String mfaSecret, int failedLoginAttempts, LocalDateTime lockedUntil,
                LocalDateTime lastLoginAt, LocalDateTime createdAt, LocalDateTime updatedAt) {
        this.id = id;
        this.email = email;
        this.passwordHash = passwordHash;
        this.firstName = firstName;
        this.lastName = lastName;
        this.status = status;
        this.roles = roles != null ? roles : new HashSet<>();
        this.emailVerified = emailVerified;
        this.emailVerificationToken = emailVerificationToken;
        this.mfaEnabled = mfaEnabled;
        this.mfaSecret = mfaSecret;
        this.failedLoginAttempts = failedLoginAttempts;
        this.lockedUntil = lockedUntil;
        this.lastLoginAt = lastLoginAt;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public boolean canAuthenticate() {
        if (status == UserStatus.DELETED || status == UserStatus.BLOCKED ||
            status == UserStatus.SUSPENDED) {
            return false;
        }
        if (status == UserStatus.LOCKED && isLocked()) {
            return false;
        }
        return true;
    }

    public boolean isLocked() {
        if (status != UserStatus.LOCKED) {
            return false;
        }
        if (lockedUntil == null) {
            return true;
        }
        return LocalDateTime.now().isBefore(lockedUntil);
    }

    public void incrementFailedAttempts() {
        this.failedLoginAttempts++;
        this.updatedAt = LocalDateTime.now();
    }

    public void resetFailedAttempts() {
        this.failedLoginAttempts = 0;
        this.updatedAt = LocalDateTime.now();
    }

    public void lock(LocalDateTime until) {
        this.status = UserStatus.LOCKED;
        this.lockedUntil = until;
        this.updatedAt = LocalDateTime.now();
    }

    public void unlock() {
        if (status == UserStatus.LOCKED) {
            this.status = UserStatus.ACTIVE;
            this.lockedUntil = null;
            this.updatedAt = LocalDateTime.now();
        }
    }

    public void confirmEmail() {
        this.emailVerified = true;
        this.emailVerificationToken = null;
        activate();
    }

    public void activate() {
        if (status == UserStatus.DELETED) {
            throw new InvalidUserStateException("Cannot activate deleted user");
        }
        this.status = UserStatus.ACTIVE;
        this.updatedAt = LocalDateTime.now();
    }

    public void suspend() {
        if (status == UserStatus.DELETED) {
            throw new InvalidUserStateException("Cannot suspend deleted user");
        }
        this.status = UserStatus.SUSPENDED;
        this.updatedAt = LocalDateTime.now();
    }

    public void block() {
        if (status == UserStatus.DELETED) {
            throw new InvalidUserStateException("Cannot block deleted user");
        }
        this.status = UserStatus.BLOCKED;
        this.updatedAt = LocalDateTime.now();
    }

    public void delete() {
        this.status = UserStatus.DELETED;
        this.updatedAt = LocalDateTime.now();
    }

    public void addRole(Role role) {
        if (status == UserStatus.DELETED) {
            throw new InvalidUserStateException("Cannot add role to deleted user");
        }
        if (role == null) {
            throw new IllegalArgumentException("Role cannot be null");
        }
        this.roles.add(role);
        this.updatedAt = LocalDateTime.now();
    }

    public void removeRole(Role role) {
        if (role == null || role.getId() == null) {
            throw new IllegalArgumentException("Role cannot be null and must have id");
        }
        this.roles.removeIf(r -> role.getId().equals(r.getId()));
        this.updatedAt = LocalDateTime.now();
    }

    public boolean hasRole(String roleName) {
        if (roleName == null || roleName.isBlank()) {
            return false;
        }
        return roles.stream()
                .anyMatch(r -> roleName.equals(r.getName()));
    }

    public boolean hasPermission(String permissionName) {
        if (permissionName == null || permissionName.isBlank()) {
            return false;
        }
        return roles.stream()
                .anyMatch(role -> role.hasPermission(permissionName));
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
        this.updatedAt = LocalDateTime.now();
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
        this.updatedAt = LocalDateTime.now();
    }

    public String getFirstName() {
        return firstName;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
        this.updatedAt = LocalDateTime.now();
    }

    public String getLastName() {
        return lastName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
        this.updatedAt = LocalDateTime.now();
    }

    public UserStatus getStatus() {
        return status;
    }

    public void setStatus(UserStatus status) {
        this.status = status;
        this.updatedAt = LocalDateTime.now();
    }

    public Set<Role> getRoles() {
        return roles;
    }

    public void setRoles(Set<Role> roles) {
        this.roles = roles != null ? roles : new HashSet<>();
        this.updatedAt = LocalDateTime.now();
    }

    public boolean isEmailVerified() {
        return emailVerified;
    }

    public void setEmailVerified(boolean emailVerified) {
        this.emailVerified = emailVerified;
        this.updatedAt = LocalDateTime.now();
    }

    public String getEmailVerificationToken() {
        return emailVerificationToken;
    }

    public void setEmailVerificationToken(String emailVerificationToken) {
        this.emailVerificationToken = emailVerificationToken;
        this.updatedAt = LocalDateTime.now();
    }

    public boolean isMfaEnabled() {
        return mfaEnabled;
    }

    public void setMfaEnabled(boolean mfaEnabled) {
        this.mfaEnabled = mfaEnabled;
        this.updatedAt = LocalDateTime.now();
    }

    public String getMfaSecret() {
        return mfaSecret;
    }

    public void setMfaSecret(String mfaSecret) {
        this.mfaSecret = mfaSecret;
        this.updatedAt = LocalDateTime.now();
    }

    public int getFailedLoginAttempts() {
        return failedLoginAttempts;
    }

    public void setFailedLoginAttempts(int failedLoginAttempts) {
        this.failedLoginAttempts = failedLoginAttempts;
        this.updatedAt = LocalDateTime.now();
    }

    public LocalDateTime getLockedUntil() {
        return lockedUntil;
    }

    public void setLockedUntil(LocalDateTime lockedUntil) {
        this.lockedUntil = lockedUntil;
        this.updatedAt = LocalDateTime.now();
    }

    public LocalDateTime getLastLoginAt() {
        return lastLoginAt;
    }

    public void setLastLoginAt(LocalDateTime lastLoginAt) {
        this.lastLoginAt = lastLoginAt;
        this.updatedAt = LocalDateTime.now();
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
