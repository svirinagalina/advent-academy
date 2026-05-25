package com.adventacademy.userservice.application.service.security;

import com.adventacademy.userservice.application.port.AuditTrailPort;
import com.adventacademy.userservice.domain.model.User;
import com.adventacademy.userservice.domain.model.UserStatus;
import com.adventacademy.userservice.domain.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AccountLockServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private AuditTrailPort auditTrailPort;

    private AccountLockService accountLockService;
    private User user;

    @BeforeEach
    void setUp() {
        Clock fixedClock = Clock.fixed(Instant.parse("2024-01-01T10:00:00Z"), ZoneOffset.UTC);
        accountLockService = new AccountLockService(userRepository, auditTrailPort, 5, 30, fixedClock);
        user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail("test@example.com");
        user.setStatus(UserStatus.ACTIVE);
        user.setFailedLoginAttempts(0);
    }

    @Test
    void handleFailedLogin_IncreasesFailedAttempts() {
        when(userRepository.save(any(User.class))).thenReturn(user);

        accountLockService.handleFailedLogin(user);

        assertEquals(1, user.getFailedLoginAttempts());
        verify(userRepository, times(1)).save(user);
    }

    @Test
    void handleFailedLogin_LocksAccountAfterMaxAttempts() {
        user.setFailedLoginAttempts(4);
        when(userRepository.save(any(User.class))).thenReturn(user);

        accountLockService.handleFailedLogin(user);

        assertEquals(5, user.getFailedLoginAttempts());
        assertEquals(UserStatus.LOCKED, user.getStatus());
        assertNotNull(user.getLockedUntil());
        verify(userRepository, times(1)).save(user);
    }

    @Test
    void unlockAccount_UnlocksUserAndResetsAttempts() {
        user.lock(user.getLockedUntil());
        user.setFailedLoginAttempts(5);

        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenReturn(user);

        accountLockService.unlockAccount(user.getId(), "127.0.0.1", "Mozilla");

        assertEquals(UserStatus.ACTIVE, user.getStatus());
        assertEquals(0, user.getFailedLoginAttempts());
        assertNull(user.getLockedUntil());
        verify(userRepository, times(1)).save(user);
    }
}