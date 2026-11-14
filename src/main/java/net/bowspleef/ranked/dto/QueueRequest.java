package net.bowspleef.ranked.dto;

import jakarta.validation.constraints.NotBlank;

public record QueueRequest(
        @NotBlank String discordId,
        @NotBlank String gameType
) {}
