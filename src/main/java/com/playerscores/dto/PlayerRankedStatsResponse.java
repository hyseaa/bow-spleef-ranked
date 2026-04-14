package com.playerscores.dto;

public record PlayerRankedStatsResponse(
        Long seasonId,
        String seasonName,
        String gameType,
        int elo,
        int matchesPlayed,
        int wins,
        String title
) {}
