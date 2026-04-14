package com.playerscores.dto;

public record PlayerSeasonEloRow(Long seasonId, String seasonName, String gameType, int elo, int matchesPlayed, String title) {}
