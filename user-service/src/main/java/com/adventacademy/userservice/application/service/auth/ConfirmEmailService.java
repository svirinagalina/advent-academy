package com.adventacademy.userservice.application.service.auth;

import com.adventacademy.userservice.application.dto.response.MessageResponse;
import com.adventacademy.userservice.domain.exception.UserNotFoundException;
import com.adventacademy.userservice.domain.model.User;
import com.adventacademy.userservice.domain.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Use case service for email address verification.
 * <p>
 * Looks up the user by the one-time verification token, marks the email as verified,
 * transitions the account from PENDING to ACTIVE, and invalidates the token.
 * </p>
 */
@Service
public class ConfirmEmailService {

    private final UserRepository userRepository;

    public ConfirmEmailService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Transactional
    public MessageResponse confirmEmail(String token) {
        User user = userRepository.findByEmailVerificationToken(token)
                .orElseThrow(() -> new UserNotFoundException("Invalid verification token"));

        user.setEmailVerified(true);
        user.activate();
        user.setEmailVerificationToken(null);
        userRepository.save(user);

        return new MessageResponse("Email confirmed successfully");
    }
}
