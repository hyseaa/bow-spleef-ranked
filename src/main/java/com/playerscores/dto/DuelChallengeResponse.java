package com.playerscores.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record DuelChallengeResponse(
        Long id,
        UUID challengerUuid,
        String challengerUsername,
        UUID challengedUuid,
        String challengedUsername,
        String gameType,
        String status,
        UUID reporterUuid,
        Integer scoreChallenger,
        Integer scoreChallenged,
        Long matchId,
        OffsetDateTime createdAt,
        OffsetDateTime expiresAt
) {}
