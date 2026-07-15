package com.playerscores.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record GameTypeRequest(
        @NotBlank @Size(max = 50) String name,
        @NotBlank @Size(max = 100) String displayName,
        boolean ranked,
        @Min(1) @Max(16) Integer teamSize
) {}
