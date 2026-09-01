package com.adventacademy.userservice.adapter.rest.controller;

import com.adventacademy.userservice.adapter.rest.converter.RsaKeyToJwkConverter;
import com.adventacademy.userservice.adapter.rest.dto.JwksResponse;
import com.adventacademy.userservice.domain.repository.RsaKeyRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.concurrent.TimeUnit;

@Slf4j
@RestController
@RequestMapping("/api/auth")
@Tag(name = "Auth - JWKS", description = "Public key endpoints for JWT validation")
public class JwksController {

    private final RsaKeyRepository rsaKeyRepository;
    private final RsaKeyToJwkConverter converter;

    public JwksController(RsaKeyRepository rsaKeyRepository, RsaKeyToJwkConverter converter) {
        this.rsaKeyRepository = rsaKeyRepository;
        this.converter = converter;
    }

    @GetMapping("/jwks")
    @Operation(
            summary = "Get JWKS (public keys)",
            description = "Retrieve active and rotating public keys in JWKS format. Resource services use this to validate JWT signatures. No authentication required.",
            operationId = "getJwks"
    )
    @ApiResponse(
            responseCode = "200",
            description = "JWKS retrieved successfully",
            content = @Content(schema = @Schema(implementation = JwksResponse.class))
    )
    public ResponseEntity<JwksResponse> getJwks() {
        var keys = rsaKeyRepository.findActiveOrRotating()
                .stream()
                .map(converter::convert)
                .toList();

        JwksResponse response = new JwksResponse(keys);

        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(1, TimeUnit.HOURS).cachePublic())
                .body(response);
    }
}
