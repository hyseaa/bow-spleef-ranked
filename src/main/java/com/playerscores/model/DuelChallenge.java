package com.playerscores.model;

import lombok.Data;

import java.time.OffsetDateTime;
import java.util.UUID;

@Data
public class DuelChallenge {
    private Long id;
    private UUID challengerUuid;
    private UUID challengedUuid;
    private String gameType;
    private String status;
    private UUID reporterUuid;
    private Integer scoreChallenger;
    private Integer scoreChallenged;
    private Long matchId;
    private OffsetDateTime createdAt;
    private OffsetDateTime expiresAt;
}
