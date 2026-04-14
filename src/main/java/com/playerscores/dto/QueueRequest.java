package com.playerscores.dto;

import jakarta.validation.constraints.NotBlank;

public record QueueRequest(
        @NotBlank String discordId,
        @NotBlank String gameType
) {}
