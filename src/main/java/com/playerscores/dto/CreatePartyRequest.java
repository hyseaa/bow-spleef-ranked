package com.playerscores.dto;

import jakarta.validation.constraints.NotBlank;

public record CreatePartyRequest(@NotBlank String leaderDiscordId) {}
