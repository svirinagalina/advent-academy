package com.adventacademy.userservice.application.service.user;

import com.adventacademy.userservice.domain.exception.UserNotFoundException;
import com.adventacademy.userservice.domain.model.User;
import com.adventacademy.userservice.domain.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class DeleteUserService {

    private final UserRepository userRepository;

    public DeleteUserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Transactional
    public void deleteUser(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));
        user.delete();
        userRepository.save(user);
    }
}
