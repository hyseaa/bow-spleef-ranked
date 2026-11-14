package net.bowspleef.ranked.dto;

import jakarta.validation.constraints.NotBlank;

public record CreatePartyRequest(@NotBlank String leaderDiscordId) {}
