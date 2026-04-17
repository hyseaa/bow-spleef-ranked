package com.playerscores.dto;

import lombok.Data;

import java.util.UUID;

@Data
public class WinsLeaderboardRow {
    private UUID uuid;
    private long wins;
}
