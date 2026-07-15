package com.playerscores.dto;

import lombok.Data;

import java.util.UUID;

@Data
public class PlayerEloSnapshot {
    private UUID playerUuid;
    private int elo;
    private double mu;
    private double sigma;
    private int matchesPlayed;
    private String rankTitle;
}
