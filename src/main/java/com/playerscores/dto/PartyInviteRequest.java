package com.playerscores.dto;

import jakarta.validation.constraints.NotBlank;

public record PartyInviteRequest(
        @NotBlank String inviterDiscordId,
        @NotBlank String inviteeDiscordId
) {}
