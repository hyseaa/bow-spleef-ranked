package com.playerscores.model;

import lombok.Data;

import java.time.OffsetDateTime;
import java.util.UUID;

@Data
public class EloHistory {
    private Long id;
    private UUID playerUuid;
    private Long matchId;
    private Long rankedSeasonId;
    private int eloBefore;
    private int eloAfter;
    private int eloChange;
    private OffsetDateTime recordedAt;
}
