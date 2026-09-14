package com.adventacademy.userservice.application.dto.response;

import java.util.List;

/** Recovery codes are returned exactly once, here; the service keeps only their digests. */
public record ConfirmMfaResponse(
        String message,
        List<String> recoveryCodes
) {}
