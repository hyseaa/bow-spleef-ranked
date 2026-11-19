package net.bowspleef.ranked.dto;

import java.util.UUID;

public record LeaderboardEntryResponse(UUID uuid, String username, int elo, int matchesPlayed, long wins, String title,
                                       long gamesPlayed, long gamesWon) {}
