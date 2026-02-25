package com.adventacademy.userservice.application.service.auth;

import com.adventacademy.userservice.application.dto.request.RegisterUserRequest;
import com.adventacademy.userservice.application.dto.response.UserResponse;
import com.adventacademy.userservice.application.port.PasswordEncoder;
import com.adventacademy.userservice.domain.exception.UserAlreadyExistsException;
import com.adventacademy.userservice.domain.model.User;
import com.adventacademy.userservice.domain.model.UserStatus;
import com.adventacademy.userservice.domain.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class RegisterUserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public RegisterUserService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
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

        // TODO: Generate email verification token
        user.setEmailVerificationToken(UUID.randomUUID().toString());

        // TODO: Send email verification (stub for now)

        User savedUser = userRepository.save(user);

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
