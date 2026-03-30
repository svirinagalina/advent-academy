package com.adventacademy.userservice.application.dto.response;

public record EnableMfaResponse(
        String secret,
        String qrCodeUrl
) {}
