package com.playerscores.model;

import lombok.Data;

import java.time.OffsetDateTime;
import java.util.UUID;

@Data
public class Party {
    private Long id;
    private UUID leaderUuid;
    private OffsetDateTime createdAt;
}
