package net.bowspleef.ranked.dto;

/**
 * A player's results in one non-ranked game type (spec 011, ADR-0022). In matches: a draw (equal best scores) is
 * neither a win nor a loss, matchesPlayed = wins + draws + losses. In games (spec 035): a match of two teams scored
 * a-b is a + b games, gamesPlayed = gamesWon + gamesLost + gamesDrawn.
 */
public record PlayerGameTypeStatsResponse(String gameType, String gameTypeDisplayName, int matchesPlayed,
                                          int wins, int losses, int draws,
                                          int gamesPlayed, int gamesWon, int gamesLost, int gamesDrawn) {}
