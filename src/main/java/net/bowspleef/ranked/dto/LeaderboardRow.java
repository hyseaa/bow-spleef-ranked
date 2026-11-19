package net.bowspleef.ranked.dto;

import lombok.Data;

import java.util.UUID;

@Data
public class LeaderboardRow {
    private UUID uuid;
    private int elo;
    private int matchesPlayed;
    private long wins;
    private long gamesPlayed;
    private long gamesWon;
    private String title;
}
