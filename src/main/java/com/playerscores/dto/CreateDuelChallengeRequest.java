package com.playerscores.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateDuelChallengeRequest(
        @NotBlank String challengerDiscordId,
        @NotBlank String challengedDiscordId,
        @NotBlank String gameType
) {}
