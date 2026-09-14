package com.adventacademy.userservice.application.service.auth;

import com.adventacademy.userservice.application.dto.request.LoginRequest;
import com.adventacademy.userservice.application.dto.response.AuthResponse;
import com.adventacademy.userservice.application.port.AuditTrailPort;
import com.adventacademy.userservice.application.port.JwtProvider;
import com.adventacademy.userservice.application.port.PasswordEncoder;
import com.adventacademy.userservice.application.service.security.AccountLockService;
import com.adventacademy.userservice.domain.exception.AccountLockedException;
import com.adventacademy.userservice.domain.exception.InvalidCredentialsException;
import com.adventacademy.userservice.domain.exception.InvalidUserStateException;
import com.adventacademy.userservice.domain.model.AuditEvent;
import com.adventacademy.userservice.domain.model.AuditEventType;
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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LoginServiceTest {

    private static final Instant FIXED_INSTANT = Instant.parse("2025-01-15T10:00:00Z");
    private static final ZoneId ZONE = ZoneId.of("UTC");
    private static final Clock FIXED_CLOCK = Clock.fixed(FIXED_INSTANT, ZONE);

    @Mock
    private UserRepository userRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private JwtProvider jwtProvider;
    @Mock
    private MfaCodeVerifier mfaCodeVerifier;
    @Mock
    private AccountLockService accountLockService;
    @Mock
    private AuditTrailPort auditTrailPort;

    private LoginService loginService;
    private User user;

    @BeforeEach
    void setUp() {
        loginService = new LoginService(
                userRepository,
                passwordEncoder,
                jwtProvider,
                mfaCodeVerifier,
                accountLockService,
                auditTrailPort,
                15L,
                FIXED_CLOCK
        );

        user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail("user@example.com");
        user.setPasswordHash("hashed-password");
        user.setStatus(UserStatus.ACTIVE);
        user.setFailedLoginAttempts(0);
        user.setMfaEnabled(false);
    }

    @Test
    void login_SuccessfulLogin_ReturnsAuthResponse() {
        LoginRequest request = new LoginRequest("user@example.com", "password", null, null, null);
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("password", "hashed-password")).thenReturn(true);
        when(jwtProvider.generateAccessToken(any(UUID.class), anyString(), any())).thenReturn("access-token");
        when(jwtProvider.generateRefreshToken(any(UUID.class))).thenReturn("refresh-token");
        when(userRepository.save(any(User.class))).thenReturn(user);

        AuthResponse response = loginService.login(request);

        assertNotNull(response);
        assertEquals("access-token", response.accessToken());
        assertEquals("refresh-token", response.refreshToken());
        assertFalse(response.mfaRequired());
        assertEquals(LocalDateTime.now(FIXED_CLOCK), user.getLastLoginAt());
        verify(userRepository).save(user);
    }

    @Test
    void login_UserNotFound_LooksExactlyLikeAWrongPassword() {
        LoginRequest unknown = new LoginRequest("unknown@example.com", "password", null, null, null);
        LoginRequest wrongPassword = new LoginRequest("user@example.com", "wrong", null, null, null);
        when(userRepository.findByEmail("unknown@example.com")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong", "hashed-password")).thenReturn(false);

        InvalidCredentialsException forUnknown = assertThrows(InvalidCredentialsException.class, () -> loginService.login(unknown));
        InvalidCredentialsException forWrong = assertThrows(InvalidCredentialsException.class, () -> loginService.login(wrongPassword));

        assertEquals(forWrong.getMessage(), forUnknown.getMessage());
        verifyNoInteractions(jwtProvider);
    }

    @Test
    void login_WrongPassword_CallsHandleFailedLoginAndThrows() {
        LoginRequest request = new LoginRequest("user@example.com", "wrong-password", null, null, null);
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong-password", "hashed-password")).thenReturn(false);

        assertThrows(InvalidCredentialsException.class, () -> loginService.login(request));
        verify(accountLockService).handleFailedLogin(user);
    }

    @Test
    void login_LockedAccount_WithCorrectPassword_ThrowsAccountLockedException() {
        // Lock until far future to ensure isLocked() returns true regardless of system clock
        user.lock(LocalDateTime.now().plusYears(1));
        LoginRequest request = new LoginRequest("user@example.com", "password", null, null, null);
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("password", "hashed-password")).thenReturn(true);

        assertThrows(AccountLockedException.class, () -> loginService.login(request));
        verifyNoInteractions(jwtProvider, accountLockService);
    }

    @Test
    void login_LockedAccount_WithWrongPassword_IsAPlainCredentialFailure() {
        // Account state is only revealed once the password verified - a probe learns nothing.
        user.lock(LocalDateTime.now().plusYears(1));
        LoginRequest request = new LoginRequest("user@example.com", "wrong", null, null, null);
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong", "hashed-password")).thenReturn(false);

        assertThrows(InvalidCredentialsException.class, () -> loginService.login(request));
        verifyNoInteractions(jwtProvider, accountLockService);
    }

    @Test
    void login_SuspendedUser_WithCorrectPassword_ThrowsInvalidUserStateException() {
        user.setStatus(UserStatus.SUSPENDED);
        LoginRequest request = new LoginRequest("user@example.com", "password", null, null, null);
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("password", "hashed-password")).thenReturn(true);

        assertThrows(InvalidUserStateException.class, () -> loginService.login(request));
        verifyNoInteractions(jwtProvider, accountLockService);
    }

    @Test
    void login_MfaEnabledNoCode_ReturnsMfaRequiredResponse() {
        user.setMfaEnabled(true);
        user.setMfaSecret("mfa-secret");
        LoginRequest request = new LoginRequest("user@example.com", "password", null, null, null);
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("password", "hashed-password")).thenReturn(true);

        AuthResponse response = loginService.login(request);

        assertTrue(response.mfaRequired());
        assertNull(response.accessToken());
        assertNull(response.refreshToken());
        verifyNoInteractions(jwtProvider);
    }

    @Test
    void login_MfaEnabledInvalidCode_ThrowsInvalidCredentialsException() {
        user.setMfaEnabled(true);
        user.setMfaSecret("mfa-secret");
        LoginRequest request = new LoginRequest("user@example.com", "password", "wrong-code", null, null);
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("password", "hashed-password")).thenReturn(true);
        when(mfaCodeVerifier.verify(user, "wrong-code")).thenReturn(MfaCodeVerifier.Outcome.REJECTED);

        assertThrows(InvalidCredentialsException.class, () -> loginService.login(request));
        verify(accountLockService).handleFailedLogin(user);
    }

    @Test
    void login_MfaEnabledValidCode_ReturnsAuthResponse() {
        user.setMfaEnabled(true);
        user.setMfaSecret("mfa-secret");
        LoginRequest request = new LoginRequest("user@example.com", "password", "123456", null, null);
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("password", "hashed-password")).thenReturn(true);
        when(mfaCodeVerifier.verify(user, "123456")).thenReturn(MfaCodeVerifier.Outcome.TOTP);
        when(jwtProvider.generateAccessToken(any(UUID.class), anyString(), any())).thenReturn("access-token");
        when(jwtProvider.generateRefreshToken(any(UUID.class))).thenReturn("refresh-token");
        when(userRepository.save(any(User.class))).thenReturn(user);

        AuthResponse response = loginService.login(request);

        assertNotNull(response);
        assertFalse(response.mfaRequired());
        assertEquals(LocalDateTime.now(FIXED_CLOCK), user.getLastLoginAt());
    }

    @Test
    void login_MfaEnabledRecoveryCode_SignsInAndRecordsRecoveryCodeUse() {
        user.setMfaEnabled(true);
        user.setMfaSecret("v1:n:c");
        LoginRequest request = new LoginRequest("user@example.com", "password", "7K3MQ-XW9RT", null, null);
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("password", "hashed-password")).thenReturn(true);
        when(mfaCodeVerifier.verify(user, "7K3MQ-XW9RT")).thenReturn(MfaCodeVerifier.Outcome.RECOVERY_CODE);
        when(jwtProvider.generateAccessToken(any(UUID.class), anyString(), any())).thenReturn("access-token");
        when(jwtProvider.generateRefreshToken(any(UUID.class))).thenReturn("refresh-token");
        when(userRepository.save(any(User.class))).thenReturn(user);

        AuthResponse response = loginService.login(request);

        assertFalse(response.mfaRequired());
        ArgumentCaptor<AuditEvent> events = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditTrailPort, times(2)).record(events.capture());
        assertEquals(AuditEventType.MFA_RECOVERY_CODE_USED, events.getAllValues().get(0).getEventType());
        assertEquals(AuditEventType.LOGIN_SUCCESS, events.getAllValues().get(1).getEventType());
    }
}
