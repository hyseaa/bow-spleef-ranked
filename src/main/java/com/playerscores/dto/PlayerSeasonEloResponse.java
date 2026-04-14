package com.playerscores.dto;

import java.util.UUID;

public record PlayerSeasonEloResponse(
        UUID playerUuid,
        Long seasonId,
        String seasonName,
        String gameType,
        int elo,
        int matchesPlayed,
        String title
) {}
