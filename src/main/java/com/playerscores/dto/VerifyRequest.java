package com.playerscores.dto;

import jakarta.validation.constraints.NotBlank;

public record VerifyRequest(
        @NotBlank String discordId,
        @NotBlank String discordUsername,
        @NotBlank String minecraftUsername) {}
