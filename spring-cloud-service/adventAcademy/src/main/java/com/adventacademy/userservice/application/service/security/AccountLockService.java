package com.adventacademy.userservice.application.service.security;

import com.adventacademy.userservice.domain.exception.UserNotFoundException;
import com.adventacademy.userservice.domain.model.User;
import com.adventacademy.userservice.domain.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Service for managing automatic account locking after repeated failed login attempts.
 * <p>
 * Increments the failed-attempt counter on each bad credential submission and locks
 * the account for a configurable duration once the maximum threshold is reached.
 * Also provides manual unlock functionality for administrators.
 * </p>
 */
@Service
public class AccountLockService {

    private final UserRepository userRepository;
    private final int maxAttempts;
    private final int lockDurationMinutes;

    public AccountLockService(
            UserRepository userRepository,
            @Value("${security.account-lock.max-failed-attempts:5}") int maxAttempts,
            @Value("${security.account-lock.lock-duration-minutes:30}") int lockDurationMinutes) {
        this.userRepository = userRepository;
        this.maxAttempts = maxAttempts;
        this.lockDurationMinutes = lockDurationMinutes;
    }

    @Transactional
    public void handleFailedLogin(User user) {
        user.incrementFailedAttempts();
        if (user.getFailedLoginAttempts() >= maxAttempts) {
            user.lock(LocalDateTime.now().plusMinutes(lockDurationMinutes));
        }
        userRepository.save(user);
    }

    @Transactional
    public void unlockAccount(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));
        user.unlock();
        user.resetFailedAttempts();
        userRepository.save(user);
    }
}
