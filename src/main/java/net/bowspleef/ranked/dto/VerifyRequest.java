package net.bowspleef.ranked.dto;

import jakarta.validation.constraints.NotBlank;

public record VerifyRequest(
        @NotBlank String discordId,
        @NotBlank String discordUsername,
        @NotBlank String minecraftUsername) {}
