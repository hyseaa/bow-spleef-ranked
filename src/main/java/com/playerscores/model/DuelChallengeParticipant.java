package com.playerscores.model;

import lombok.Data;

import java.util.UUID;

@Data
public class DuelChallengeParticipant {
    public static final String SIDE_CHALLENGER = "CHALLENGER";
    public static final String SIDE_CHALLENGED = "CHALLENGED";

    private Long id;
    private Long challengeId;
    private UUID playerUuid;
    private String side;
}
