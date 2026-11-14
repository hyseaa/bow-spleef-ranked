package net.bowspleef.ranked.dto;

import lombok.Data;

/** What the database knows of a player's matches in one non-ranked game type. */
@Data
public class PlayerGameTypeStatsRow {
    private String gameType;
    private String gameTypeDisplayName;
    private int matchesPlayed;
    private int wins;
    private int draws;
}
