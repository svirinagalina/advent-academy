package com.adventacademy.userservice.application.service.user;

import com.adventacademy.userservice.domain.exception.UserNotFoundException;
import com.adventacademy.userservice.domain.model.User;
import com.adventacademy.userservice.domain.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Use case service for removing users.
 * <p>
 * Performs a soft delete by transitioning the user's status to DELETED
 * without removing the record from the database, preserving audit history.
 * </p>
 */
@Service
public class DeleteUserService {

    private final UserRepository userRepository;
    private final com.adventacademy.userservice.application.service.admin.UserAdminPolicy adminPolicy;

    public DeleteUserService(UserRepository userRepository, com.adventacademy.userservice.application.service.admin.UserAdminPolicy adminPolicy) {
        this.userRepository = userRepository; this.adminPolicy=adminPolicy;
    }

    @Transactional
    public void deleteUser(UUID userId) {
        adminPolicy.lock(); adminPolicy.authorizeRequestIfPresent(); adminPolicy.protect(userId);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));
        user.delete();
        userRepository.save(user);
    }
}
