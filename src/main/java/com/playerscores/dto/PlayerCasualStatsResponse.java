package com.playerscores.dto;

/** A draw (equal best scores) is neither a win nor a loss: matchesPlayed = wins + draws + losses. */
public record PlayerCasualStatsResponse(int matchesPlayed, int wins, int losses, int draws) {}
