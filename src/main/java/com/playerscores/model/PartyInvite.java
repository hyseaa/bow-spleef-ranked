package com.playerscores.model;

import lombok.Data;

import java.time.OffsetDateTime;
import java.util.UUID;

@Data
public class PartyInvite {
    private Long id;
    private Long partyId;
    private UUID playerUuid;
    private OffsetDateTime createdAt;
    private OffsetDateTime expiresAt;
}
