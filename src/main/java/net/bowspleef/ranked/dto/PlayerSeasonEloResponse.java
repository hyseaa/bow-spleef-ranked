package net.bowspleef.ranked.dto;

import java.util.UUID;

public record PlayerSeasonEloResponse(
        UUID playerUuid,
        Long seasonId,
        String seasonName,
        String gameType,
        int elo,
        int matchesPlayed,
        int gamesPlayed,
        int gamesWon,
        int gamesLost,
        String title
) {}
