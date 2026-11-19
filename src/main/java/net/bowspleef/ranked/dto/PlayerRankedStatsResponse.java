package net.bowspleef.ranked.dto;

import lombok.Data;

/**
 * A player's figures in one active season. {@code matchesPlayed}, {@code wins}, {@code losses} and {@code draws}
 * count matches; the {@code games*} fields count games, a best-of scored a-b being a + b games (spec 035).
 */
@Data
public class PlayerRankedStatsResponse {
    private Long seasonId;
    private String seasonName;
    private String gameType;
    private int elo;
    private int matchesPlayed;
    private int wins;
    private int losses;
    private int draws;
    private int gamesPlayed;
    private int gamesWon;
    private int gamesLost;
    private int gamesDrawn;
    private String title;
}
