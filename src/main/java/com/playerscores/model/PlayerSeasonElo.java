package com.playerscores.model;

import lombok.Data;

import java.util.UUID;

@Data
public class PlayerSeasonElo {
    private UUID playerUuid;
    private Long rankedSeasonId;
    private int elo;
    private int matchesPlayed;
}
