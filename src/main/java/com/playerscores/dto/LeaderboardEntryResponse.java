package com.playerscores.dto;

public record LeaderboardEntryResponse(Long playerId, String username, Integer bestScore, String game) {}
