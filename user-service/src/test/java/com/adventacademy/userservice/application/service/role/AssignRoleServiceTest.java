package com.adventacademy.userservice.application.service.role;

import com.adventacademy.userservice.application.port.AuditTrailPort;
import com.adventacademy.userservice.domain.model.AuditEvent;
import com.adventacademy.userservice.domain.model.AuditEventType;
import com.adventacademy.userservice.domain.model.AuditResult;
import com.adventacademy.userservice.domain.model.Role;
import com.adventacademy.userservice.domain.model.User;
import com.adventacademy.userservice.domain.repository.RoleRepository;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AssignRoleServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private RoleRepository roleRepository;
    @Mock private AuditTrailPort auditTrailPort;

    private AssignRoleService service;
    private final Clock clock = Clock.fixed(Instant.parse("2026-05-28T10:00:00Z"), ZoneId.of("UTC"));

    private UUID userId;
    private UUID roleId;
    private User user;
    private Role role;

    @BeforeEach
    void setUp() {
        service = new AssignRoleService(userRepository, roleRepository, auditTrailPort, clock);
        userId = UUID.randomUUID();
        roleId = UUID.randomUUID();
        user = new User();
        user.setId(userId);
        role = new Role();
        role.setId(roleId);
        role.setName("EDITOR");
    }

    @Test
    void assignRoleToUser_recordsRoleAssignedEventWithRoleMetadata() {
        when(userRepository.findByIdWithRoles(userId)).thenReturn(Optional.of(user));
        when(roleRepository.findById(roleId)).thenReturn(Optional.of(role));
        when(userRepository.save(any(User.class))).thenReturn(user);

        service.assignRoleToUser(userId, roleId, "10.0.0.5", "Chrome");

        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditTrailPort).record(captor.capture());
        AuditEvent event = captor.getValue();

        assertThat(event.getEventType()).isEqualTo(AuditEventType.ROLE_ASSIGNED);
        assertThat(event.getResult()).isEqualTo(AuditResult.SUCCESS);
        assertThat(event.getActorUserId()).isEqualTo(userId);
        assertThat(event.getTargetUserId()).isEqualTo(userId);
        assertThat(event.getMetadata())
                .containsEntry("roleId", roleId.toString())
                .containsEntry("roleName", "EDITOR");
    }

    @Test
    void removeRoleFromUser_recordsRoleRevokedEventWithRoleMetadata() {
        user.addRole(role);
        when(userRepository.findByIdWithRoles(userId)).thenReturn(Optional.of(user));
        when(roleRepository.findById(roleId)).thenReturn(Optional.of(role));
        when(userRepository.save(any(User.class))).thenReturn(user);

        service.removeRoleFromUser(userId, roleId, "10.0.0.5", "Chrome");

        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditTrailPort).record(captor.capture());
        AuditEvent event = captor.getValue();

        assertThat(event.getEventType()).isEqualTo(AuditEventType.ROLE_REVOKED);
        assertThat(event.getResult()).isEqualTo(AuditResult.SUCCESS);
        assertThat(event.getMetadata())
                .containsEntry("roleId", roleId.toString())
                .containsEntry("roleName", "EDITOR");
    }
}
