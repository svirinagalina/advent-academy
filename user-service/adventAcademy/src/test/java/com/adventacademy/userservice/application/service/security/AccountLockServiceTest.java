package com.adventacademy.userservice.application.service.security;

import com.adventacademy.userservice.domain.model.User;
import com.adventacademy.userservice.domain.model.UserStatus;
import com.adventacademy.userservice.domain.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AccountLockServiceTest {

    @Mock
    private UserRepository userRepository;

    private AccountLockService accountLockService;
    private User user;

    @BeforeEach
    void setUp() {
        accountLockService = new AccountLockService(userRepository, 5, 30);

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

        accountLockService.unlockAccount(user.getId());

        assertEquals(UserStatus.ACTIVE, user.getStatus());
        assertEquals(0, user.getFailedLoginAttempts());
        assertNull(user.getLockedUntil());
        verify(userRepository, times(1)).save(user);
    }
}
