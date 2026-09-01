package com.adventacademy.userservice.adapter.rest.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record JwkDTO(
        @JsonProperty("kty")
        String keyType,
        @JsonProperty("use")
        String use,
        @JsonProperty("kid")
        String kid,
        @JsonProperty("alg")
        String algorithm,
        @JsonProperty("n")
        String modulus,
        @JsonProperty("e")
        String exponent
) {}
