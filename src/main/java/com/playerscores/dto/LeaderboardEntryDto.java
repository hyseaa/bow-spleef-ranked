package com.playerscores.dto;

import lombok.Data;

@Data
public class LeaderboardEntryDto {
    private Long playerId;
    private String username;
    private Integer bestScore;
    private String game;
}
