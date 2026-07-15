package com.playerscores.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * For team game types (teamSize >= 2), {@code challengedDiscordId} may be ANY member
 * of the opposing party; the challenge is addressed to that party's leader.
 */
public record CreateDuelChallengeRequest(
        @NotBlank String challengerDiscordId,
        @NotBlank String challengedDiscordId,
        @NotBlank String gameType
) {}
