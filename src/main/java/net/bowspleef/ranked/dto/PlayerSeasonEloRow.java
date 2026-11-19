package net.bowspleef.ranked.dto;

import lombok.Data;

@Data
public class PlayerSeasonEloRow {
    private Long seasonId;
    private String seasonName;
    private String gameType;
    private int elo;
    private int matchesPlayed;
    private int gamesPlayed;
    private int gamesWon;
    private int gamesLost;
    private String title;
}
