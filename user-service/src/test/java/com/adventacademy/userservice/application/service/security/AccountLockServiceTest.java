package com.adventacademy.userservice.application.service.security;

import com.adventacademy.userservice.application.port.AuditTrailPort;
import com.adventacademy.userservice.domain.model.AuditEvent;
import com.adventacademy.userservice.domain.model.AuditEventType;
import com.adventacademy.userservice.domain.model.AuditResult;
import com.adventacademy.userservice.domain.model.User;
import com.adventacademy.userservice.domain.model.UserStatus;
import com.adventacademy.userservice.domain.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AccountLockServiceTest {

    private static final Instant FIXED_INSTANT = Instant.parse("2025-01-15T10:00:00Z");
    private static final ZoneId ZONE = ZoneId.of("UTC");
    private static final Clock FIXED_CLOCK = Clock.fixed(FIXED_INSTANT, ZONE);

    @Mock
    private UserRepository userRepository;

    @Mock
    private AuditTrailPort auditTrailPort;

    private AccountLockService accountLockService;
    private User user;

    @BeforeEach
    void setUp() {
        accountLockService = new AccountLockService(userRepository, auditTrailPort, 5, 30, FIXED_CLOCK);

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
        LocalDateTime expectedLockUntil = LocalDateTime.now(FIXED_CLOCK).plusMinutes(30);
        assertEquals(expectedLockUntil, user.getLockedUntil());
        verify(userRepository, times(1)).save(user);

        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditTrailPort).record(captor.capture());
        AuditEvent event = captor.getValue();
        assertThat(event.getEventType()).isEqualTo(AuditEventType.USER_BLOCKED);
        assertThat(event.getResult()).isEqualTo(AuditResult.SUCCESS);
        assertThat(event.getTargetUserId()).isEqualTo(user.getId());
        assertThat(event.getReason()).contains("Account locked after 5 failed attempts");
    }

    @Test
    void handleFailedLogin_DoesNotLockBeforeMaxAttempts() {
        user.setFailedLoginAttempts(3);
        when(userRepository.save(any(User.class))).thenReturn(user);

        accountLockService.handleFailedLogin(user);

        assertEquals(4, user.getFailedLoginAttempts());
        assertEquals(UserStatus.ACTIVE, user.getStatus());
        assertNull(user.getLockedUntil());
        verify(userRepository, times(1)).save(user);
    }

    @Test
    void unlockAccount_UnlocksUserAndResetsAttempts() {
        user.lock(LocalDateTime.now(FIXED_CLOCK).plusMinutes(30));
        user.setFailedLoginAttempts(5);
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenReturn(user);

        accountLockService.unlockAccount(user.getId(), "127.0.0.1", "Mozilla");

        assertEquals(UserStatus.ACTIVE, user.getStatus());
        assertEquals(0, user.getFailedLoginAttempts());
        assertNull(user.getLockedUntil());
        verify(userRepository, times(1)).save(user);

        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditTrailPort).record(captor.capture());
        AuditEvent event = captor.getValue();
        assertThat(event.getEventType()).isEqualTo(AuditEventType.USER_UNBLOCKED);
        assertThat(event.getResult()).isEqualTo(AuditResult.SUCCESS);
        assertThat(event.getActorUserId()).isEqualTo(user.getId());
        assertThat(event.getTargetUserId()).isEqualTo(user.getId());
        assertThat(event.getIpAddress()).isEqualTo("127.0.0.1");
        assertThat(event.getUserAgent()).isEqualTo("Mozilla");
    }
}