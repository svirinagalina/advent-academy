package com.adventacademy.userservice.application.service.auth;

import com.adventacademy.userservice.application.dto.request.RefreshTokenRequest;
import com.adventacademy.userservice.application.port.AuditTrailPort;
import com.adventacademy.userservice.application.port.JwtProvider;
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
import java.time.ZoneId;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private JwtProvider jwtProvider;
    @Mock private AuditTrailPort auditTrailPort;

    private RefreshTokenService service;
    private final Clock clock = Clock.fixed(Instant.parse("2026-05-28T10:00:00Z"), ZoneId.of("UTC"));

    @BeforeEach
    void setUp() {
        service = new RefreshTokenService(userRepository, jwtProvider, auditTrailPort, clock, 15L);
    }

    @Test
    void refresh_validToken_recordsRefreshTokenUpdatedEvent() {
        User user = new User();
        UUID userId = UUID.randomUUID();
        user.setId(userId);
        user.setEmail("user@test.com");
        user.setStatus(UserStatus.ACTIVE);
        user.setEmailVerified(true);

        when(jwtProvider.validateRefreshToken("rt")).thenReturn(true);
        when(jwtProvider.consumeRefreshToken("rt")).thenReturn(true);
        when(jwtProvider.getUserIdFromToken("rt")).thenReturn(userId);
        when(userRepository.findByIdForUpdate(userId)).thenReturn(Optional.of(user));
        when(jwtProvider.generateAccessToken(any(UUID.class), anyString(), any())).thenReturn("at-new");
        when(jwtProvider.generateRefreshToken(userId)).thenReturn("rt-new");

        service.refresh(new RefreshTokenRequest("rt", "10.0.0.4", "okhttp"));

        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditTrailPort).record(captor.capture());
        AuditEvent event = captor.getValue();

        assertThat(event.getEventType()).isEqualTo(AuditEventType.REFRESH_TOKEN_UPDATED);
        assertThat(event.getResult()).isEqualTo(AuditResult.SUCCESS);
        assertThat(event.getActorUserId()).isEqualTo(userId);
        assertThat(event.getTargetUserId()).isEqualTo(userId);
        assertThat(event.getIpAddress()).isEqualTo("10.0.0.4");
        assertThat(event.getUserAgent()).isEqualTo("okhttp");
    }
}
