package com.adventacademy.userservice.application.service.auth;

import com.adventacademy.userservice.application.dto.response.MessageResponse;
import com.adventacademy.userservice.domain.exception.UserNotFoundException;
import com.adventacademy.userservice.domain.model.User;
import com.adventacademy.userservice.domain.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
