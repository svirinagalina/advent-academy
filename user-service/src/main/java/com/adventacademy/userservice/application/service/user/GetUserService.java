package com.adventacademy.userservice.application.service.user;

import com.adventacademy.userservice.application.dto.response.UserResponse;
import com.adventacademy.userservice.domain.exception.UserNotFoundException;
import com.adventacademy.userservice.domain.model.User;
import com.adventacademy.userservice.domain.pagination.PageResult;
import com.adventacademy.userservice.domain.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import com.adventacademy.userservice.domain.pagination.PageRequest;
import com.adventacademy.userservice.domain.pagination.Sort;

/**
 * Use case service for retrieving user data.
 * <p>
 * Provides read-only access to a single user by ID or to the full list of users.
 * Throws {@link com.adventacademy.userservice.domain.exception.UserNotFoundException}
 * when the requested user does not exist.
 * </p>
 */
@Service
public class GetUserService {

    private final UserRepository userRepository;

    public GetUserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public UserResponse getUserById(UUID userId) {
        User user = userRepository.findByIdWithRoles(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));
        return mapToUserResponse(user);
    }

    @Transactional(readOnly = true)
    public PageResult<UserResponse> getAllUsers(int page, int size, String sort) {
        PageRequest pageRequest = new PageRequest(
                page,
                size,
                parseSort(sort)
        );

        return userRepository.findAll(pageRequest)
                .map(this::mapToUserResponse);
    }

    private Sort parseSort(String sortParam) {
        if (sortParam == null || sortParam.isBlank()) {
            return Sort.UNSORTED;
        }

        String[] parts = sortParam.split(",");
        String field = parts[0].trim();
        String direction = parts.length > 1 ? parts[1].trim().toLowerCase() : "asc";

        Sort.Order order = switch (direction) {
            case "desc" -> Sort.Order.desc(field);
            default -> Sort.Order.asc(field);
        };

        return Sort.by(order);
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
