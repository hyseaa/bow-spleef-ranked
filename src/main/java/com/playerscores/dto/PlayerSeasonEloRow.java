package com.playerscores.dto;

import lombok.Data;

@Data
public class PlayerSeasonEloRow {
    private Long seasonId;
    private String seasonName;
    private String gameType;
    private int elo;
    private int matchesPlayed;
    private String title;
}
