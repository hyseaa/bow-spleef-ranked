package com.playerscores.dto;

import java.util.UUID;

public record LeaderboardRow(UUID uuid, int elo, int matchesPlayed, long wins, String title) {}
