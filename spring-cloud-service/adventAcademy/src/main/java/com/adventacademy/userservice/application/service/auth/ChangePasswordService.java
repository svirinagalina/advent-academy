package com.adventacademy.userservice.application.service.auth;

import com.adventacademy.userservice.application.dto.request.ChangePasswordRequest;
import com.adventacademy.userservice.application.dto.response.MessageResponse;
import com.adventacademy.userservice.application.port.PasswordEncoder;
import com.adventacademy.userservice.domain.exception.InvalidCredentialsException;
import com.adventacademy.userservice.domain.exception.UserNotFoundException;
import com.adventacademy.userservice.domain.model.User;
import com.adventacademy.userservice.domain.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Use case service for changing a user's password.
 * <p>
 * Verifies the current password before replacing it with the new encoded value.
 * Throws {@link com.adventacademy.userservice.domain.exception.InvalidCredentialsException}
 * if the old password does not match.
 * </p>
 */
@Service
public class ChangePasswordService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public ChangePasswordService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public MessageResponse changePassword(UUID userId, ChangePasswordRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));

        if (!passwordEncoder.matches(request.oldPassword(), user.getPasswordHash())) {
            throw new InvalidCredentialsException("Old password is incorrect");
        }

        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        userRepository.save(user);

        return new MessageResponse("Password changed successfully");
    }
}
