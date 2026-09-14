package com.adventacademy.userservice.application.service.security.auth;

import com.adventacademy.userservice.application.dto.request.LoginRequest;
import com.adventacademy.userservice.application.port.AuditTrailPort;
import com.adventacademy.userservice.application.port.JwtProvider;
import com.adventacademy.userservice.application.service.auth.MfaCodeVerifier;
import com.adventacademy.userservice.application.port.PasswordEncoder;
import com.adventacademy.userservice.application.service.auth.LoginService;
import com.adventacademy.userservice.application.service.security.AccountLockService;
import com.adventacademy.userservice.domain.exception.InvalidCredentialsException;
import com.adventacademy.userservice.domain.model.AuditEvent;
import com.adventacademy.userservice.domain.model.AuditEventType;
import com.adventacademy.userservice.domain.model.AuditResult;
import com.adventacademy.userservice.domain.model.User;
import com.adventacademy.userservice.domain.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LoginServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private JwtProvider jwtProvider;
    @Mock private MfaCodeVerifier mfaCodeVerifier;
    @Mock private AccountLockService accountLockService;
    @Mock private AuditTrailPort auditTrailPort;

    private LoginService loginService;
    private final Clock clock = Clock.fixed(Instant.now(), ZoneId.systemDefault());

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
                clock
        );
    }

    @Test
    void login_success_shouldRecordAuditEvent() {
        User user = new User();
        LoginRequest request = new LoginRequest(
                "test@test.com", "password", null, "127.0.0.1", "Mozilla"
        );

        when(userRepository.findByEmail(request.email())).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(any(), any())).thenReturn(true);
        when(jwtProvider.generateAccessToken(any(), any(), any())).thenReturn("accessToken");
        when(jwtProvider.generateRefreshToken(any())).thenReturn("refreshToken");

        loginService.login(request);

        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditTrailPort).record(captor.capture());

        AuditEvent event = captor.getValue();
        assertThat(event.getEventType()).isEqualTo(AuditEventType.LOGIN_SUCCESS);
        assertThat(event.getResult()).isEqualTo(AuditResult.SUCCESS);
        assertThat(event.getIpAddress()).isEqualTo("127.0.0.1");
        assertThat(event.getUserAgent()).isEqualTo("Mozilla");
    }

    @Test
    void login_wrongPassword_shouldRecordFailedAuditEvent() {
        User user = new User();
        LoginRequest request = new LoginRequest(
                "test@test.com", "wrongpassword", null, "127.0.0.1", "Mozilla"
        );

        when(userRepository.findByEmail(request.email())).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(any(), any())).thenReturn(false);

        assertThatThrownBy(() -> loginService.login(request))
                .isInstanceOf(InvalidCredentialsException.class);

        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditTrailPort).record(captor.capture());

        AuditEvent event = captor.getValue();
        assertThat(event.getEventType()).isEqualTo(AuditEventType.LOGIN_FAILED);
        assertThat(event.getResult()).isEqualTo(AuditResult.FAILED);
        assertThat(event.getReason()).isEqualTo("Invalid credentials");
    }
}
