package com.adventacademy.userservice.adapter.rest.controller;

import com.adventacademy.userservice.application.service.security.KeyRotationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/admin/keys")
@Tag(name = "Key Management", description = "RSA key management for admins only")
@PreAuthorize("hasRole('ADMIN')")
public class KeyManagementController {

    private final KeyRotationService keyRotationService;

    public KeyManagementController(KeyRotationService keyRotationService) {
        this.keyRotationService = keyRotationService;
    }

    @PostMapping("/rotate")
    @Operation(
            summary = "Rotate RSA keys",
            description = "Manually trigger RSA key rotation. New key becomes active, old keys transition to ROTATING for a TTL window.",
            operationId = "rotateKeys"
    )
    @ApiResponse(responseCode = "200", description = "Key rotation initiated")
    @ApiResponse(responseCode = "403", description = "Requires ADMIN role")
    public ResponseEntity<String> rotateKeys() {
        log.info("Manual key rotation initiated by admin");
        keyRotationService.rotateKeys();
        return ResponseEntity.ok("Key rotation initiated. Old keys will be revoked after rotation window.");
    }

    @PostMapping("/emergency-revoke/{kid}")
    @Operation(
            summary = "Emergency revoke key",
            description = "Immediately revoke a key in case of suspected compromise.",
            operationId = "emergencyRevokeKey"
    )
    @ApiResponse(responseCode = "200", description = "Key revoked")
    @ApiResponse(responseCode = "403", description = "Requires ADMIN role")
    @ApiResponse(responseCode = "404", description = "Key not found")
    public ResponseEntity<String> emergencyRevokeKey(@PathVariable String kid) {
        log.warn("Emergency key revocation for kid: {} initiated by admin", kid);
        keyRotationService.emergencyRevokeKey(kid);
        return ResponseEntity.ok("Key " + kid + " has been revoked immediately.");
    }
}
