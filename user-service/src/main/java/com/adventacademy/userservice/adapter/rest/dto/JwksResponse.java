package com.adventacademy.userservice.adapter.rest.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record JwksResponse(
        @JsonProperty("keys")
        List<JwkDTO> keys
) {}
