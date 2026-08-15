package com.adventacademy.userservice.adapter.rest.controller;

import com.adventacademy.userservice.application.dto.request.RoleRequestDecisionRequest;
import com.adventacademy.userservice.application.dto.request.SubmitRoleRequestRequest;
import com.adventacademy.userservice.application.dto.response.RoleRequestResponse;
import com.adventacademy.userservice.application.service.role.RoleRequestService;
import com.adventacademy.userservice.domain.pagination.PageResult;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * REST adapter for role approval requests.
 *
 * <p>Users submit requests through the nested user endpoint, while admins can
 * read all requests from the top-level collection. The controller stays thin:
 * it maps HTTP input to the application use case and does not contain approval
 * business rules.</p>
 */
@Validated
@RestController
@Tag(name = "Role Requests", description = "Role approval request endpoints")
public class RoleRequestController {

    private final RoleRequestService roleRequestService;

    public RoleRequestController(RoleRequestService roleRequestService) {
        this.roleRequestService = roleRequestService;
    }

    @PostMapping("/api/users/{userId}/role-requests")
    @PreAuthorize("#userId.toString() == authentication.name or hasRole('ADMIN')")
    @Operation(
            summary = "Submit a role request",
            description = "Create a PENDING request for assigning a role",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    required = true,
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = SubmitRoleRequestRequest.class),
                            examples = @ExampleObject(
                                    name = "Role request",
                                    value = "{\"requestedRoleId\":\"11111111-1111-1111-1111-111111111111\"}"
                            )
                    )
            )
    )
    @ApiResponse(responseCode = "201", description = "Role request created")
    @ApiResponse(responseCode = "404", description = "User or role not found")
    @ApiResponse(responseCode = "409", description = "Active role request already exists")
    public ResponseEntity<RoleRequestResponse> submit(
            @Parameter(description = "User requesting the role", example = "33333333-3333-3333-3333-333333333333")
            @PathVariable UUID userId,
            @Valid @RequestBody SubmitRoleRequestRequest request,
            HttpServletRequest httpRequest) {
        RoleRequestResponse response = roleRequestService.submit(
                userId,
                request.requestedRoleId(),
                httpRequest.getRemoteAddr(),
                httpRequest.getHeader("User-Agent"));
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/api/users/{userId}/role-requests")
    @PreAuthorize("#userId.toString() == authentication.name or hasRole('ADMIN')")
    @Operation(summary = "List user role requests", description = "Retrieve role requests for a single user")
    @ApiResponse(responseCode = "200", description = "Role requests returned")
    @ApiResponse(responseCode = "404", description = "User not found")
    public ResponseEntity<PageResult<RoleRequestResponse>> getForUser(
            @Parameter(description = "User whose requests should be returned", example = "33333333-3333-3333-3333-333333333333")
            @PathVariable UUID userId,
            @Parameter(description = "Zero-based page index", example = "0")
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @Parameter(description = "Page size", example = "20")
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @Parameter(description = "Sort format: field,direction", example = "createdAt,desc")
            @RequestParam(defaultValue = "createdAt,desc") String sort) {
        return ResponseEntity.ok(roleRequestService.getForUser(userId, page, size, sort));
    }

    @PostMapping("/api/role-requests/{id}/decision")
    @Operation(
            summary = "Record an admin decision on a role request",
            description = "Completes the pending Camunda review task with the decision. The request "
                    + "stays PENDING in this response — approval/rejection is applied asynchronously "
                    + "by the Camunda job worker once the process routes the gateway.",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    required = true,
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = RoleRequestDecisionRequest.class),
                            examples = @ExampleObject(
                                    name = "Approve",
                                    value = "{\"decision\":\"APPROVE\",\"reviewerId\":"
                                            + "\"44444444-4444-4444-4444-444444444444\"}"
                            )
                    )
            )
    )
    @ApiResponse(responseCode = "202", description = "Decision accepted, processing asynchronously")
    @ApiResponse(responseCode = "404", description = "Role request not found")
    @ApiResponse(responseCode = "409", description = "Role request already decided")
    public ResponseEntity<RoleRequestResponse> decide(
            @Parameter(description = "Role request being decided", example = "22222222-2222-2222-2222-222222222222")
            @PathVariable UUID id,
            @Valid @RequestBody RoleRequestDecisionRequest request) {
        RoleRequestResponse response = roleRequestService.submitDecision(
                id, request.reviewerId(), request.decision(), request.reason());
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }

    @GetMapping("/api/role-requests")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "List all role requests", description = "Retrieve role requests for admin review")
    @ApiResponse(responseCode = "200", description = "Role requests returned")
    public ResponseEntity<PageResult<RoleRequestResponse>> getAll(
            @Parameter(description = "Zero-based page index", example = "0")
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @Parameter(description = "Page size", example = "20")
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @Parameter(description = "Sort format: field,direction", example = "createdAt,desc")
            @RequestParam(defaultValue = "createdAt,desc") String sort) {
        return ResponseEntity.ok(roleRequestService.getAll(page, size, sort));
    }
}
