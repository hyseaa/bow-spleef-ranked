package net.bowspleef.ranked.dto;

/**
 * A player's results in one non-ranked game type (spec 011, ADR-0022). A draw (equal best scores) is neither a win
 * nor a loss: matchesPlayed = wins + draws + losses.
 */
public record PlayerGameTypeStatsResponse(String gameType, String gameTypeDisplayName, int matchesPlayed,
                                          int wins, int losses, int draws) {}
