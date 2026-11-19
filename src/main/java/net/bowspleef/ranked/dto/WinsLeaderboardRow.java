package net.bowspleef.ranked.dto;

import lombok.Data;

import java.util.UUID;

@Data
public class WinsLeaderboardRow {
    private UUID uuid;
    private long wins;
    private long gamesWon;
}
