package net.bowspleef.ranked.dto;

import lombok.Data;

/** What the database knows of a player's matches and games in one non-ranked game type (spec 011, spec 035). */
@Data
public class PlayerGameTypeStatsRow {
    private String gameType;
    private String gameTypeDisplayName;
    private int matchesPlayed;
    private int wins;
    private int draws;
    private int gamesWon;
    private int gamesLost;
    private int gamesDrawn;
}
