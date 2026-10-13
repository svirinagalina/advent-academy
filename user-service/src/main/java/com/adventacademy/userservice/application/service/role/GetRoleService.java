package com.adventacademy.userservice.application.service.role;

import com.adventacademy.userservice.application.dto.response.RoleResponse;
import com.adventacademy.userservice.domain.exception.RoleNotFoundException;
import com.adventacademy.userservice.domain.model.Role;
import com.adventacademy.userservice.domain.pagination.PageRequest;
import com.adventacademy.userservice.domain.pagination.PageResult;
import com.adventacademy.userservice.domain.pagination.Sort;
import com.adventacademy.userservice.domain.repository.RoleRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class GetRoleService {

    private final RoleRepository roleRepository;

    public GetRoleService(RoleRepository roleRepository) {
        this.roleRepository = roleRepository;
    }

    @Transactional(readOnly = true)
    public PageResult<RoleResponse> getAllRoles(int page, int size, String sort) {
        PageRequest pageRequest = PageRequest.of(page, size, parseSort(sort));

        return roleRepository.findAll(pageRequest)
                .map(this::mapToRoleResponse);
    }

    private Sort parseSort(String sortParam) {
        if (sortParam == null || sortParam.isBlank()) {
            return Sort.by(Sort.Order.asc("id"));
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

    @Transactional(readOnly = true)
    public RoleResponse getRoleById(UUID id) {
        Role role = roleRepository.findByIdWithPermissions(id)
                .orElseThrow(() -> new RoleNotFoundException(id));
        return mapToRoleResponse(role);
    }

    private RoleResponse mapToRoleResponse(Role role) {
        return new RoleResponse(
                role.getId(),
                role.getName(),
                role.getDescription(),
                role.getPermissions().stream()
                        .map(permission -> permission.getName())
                        .collect(Collectors.toSet()),
                role.isSystemRole(),
                role.getCreatedAt()
        );
    }
}