package com.playerscores.dto;

import jakarta.validation.constraints.NotBlank;

public record RankedSeasonRequest(
        @NotBlank String name,
        @NotBlank String gameType
) {}
