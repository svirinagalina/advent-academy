package com.adventacademy.userservice.application.service.auth;

import com.adventacademy.userservice.application.dto.request.RegisterUserRequest;
import com.adventacademy.userservice.application.dto.response.UserResponse;
import com.adventacademy.userservice.application.port.AuditTrailPort;
import com.adventacademy.userservice.application.port.PasswordEncoder;
import com.adventacademy.userservice.domain.exception.UserAlreadyExistsException;
import com.adventacademy.userservice.domain.model.AuditEvent;
import com.adventacademy.userservice.domain.model.AuditEventType;
import com.adventacademy.userservice.domain.model.AuditResult;
import com.adventacademy.userservice.domain.model.User;
import com.adventacademy.userservice.domain.model.UserStatus;
import com.adventacademy.userservice.domain.model.VerificationToken;
import com.adventacademy.userservice.domain.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class RegisterUserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final VerificationTokenService tokens;
    private final EmailDispatcher emails;
    private final AuditTrailPort auditTrailPort;
    private final Clock clock;

    public RegisterUserService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            VerificationTokenService tokens,
            EmailDispatcher emails,
            AuditTrailPort auditTrailPort,
            Clock clock) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokens = tokens;
        this.emails = emails;
        this.auditTrailPort = auditTrailPort;
        this.clock = clock;
    }

    @Transactional
    public UserResponse register(RegisterUserRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new UserAlreadyExistsException(request.email());
        }

        User user = new User();
        user.setEmail(request.email());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setFirstName(request.firstName());
        user.setLastName(request.lastName());
        user.setStatus(UserStatus.PENDING);

        User savedUser = userRepository.save(user);

        // The verification link carries a random token whose digest alone is stored; the
        // e-mail goes out after this transaction commits (see EmailDispatcher).
        String verificationToken = tokens.issue(savedUser.getId(), VerificationToken.PURPOSE_EMAIL_VERIFICATION);
        emails.sendEmailVerification(savedUser.getId(), savedUser.getEmail(), verificationToken);

        auditTrailPort.record(new AuditEvent(
                UUID.randomUUID(),
                AuditEventType.USER_REGISTERED,
                savedUser.getId(),
                savedUser.getId(),
                LocalDateTime.now(clock),
                request.ipAddress(),
                request.userAgent(),
                AuditResult.SUCCESS,
                null,
                null
        ));

        return mapToUserResponse(savedUser);
    }

    private UserResponse mapToUserResponse(User user) {
        return new UserResponse(
                user.getId(),
                user.getEmail(),
                user.getFirstName(),
                user.getLastName(),
                user.getStatus(),
                user.getRoles().stream()
                        .map(role -> role.getName())
                        .collect(Collectors.toSet()),
                user.isEmailVerified(),
                user.isMfaEnabled(),
                user.getLastLoginAt(),
                user.getCreatedAt()
        );
    }
}