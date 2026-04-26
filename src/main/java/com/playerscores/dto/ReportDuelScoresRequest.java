package com.playerscores.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record ReportDuelScoresRequest(
        @NotBlank String reporterDiscordId,
        @NotNull @Min(0) Integer scoreChallenger,
        @NotNull @Min(0) Integer scoreChallenged
) {}
