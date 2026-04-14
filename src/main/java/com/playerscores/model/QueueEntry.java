package com.playerscores.model;

import lombok.Data;

import java.time.OffsetDateTime;
import java.util.UUID;

@Data
public class QueueEntry {
    private UUID playerUuid;
    private String gameType;
    private Long rankedSeasonId;
    private int elo;
    private OffsetDateTime queuedAt;
}
