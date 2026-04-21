package com.playerscores.dto;

import lombok.Data;

import java.util.UUID;

@Data
public class OpponentRow {
    private Long matchId;
    private UUID uuid;
}
