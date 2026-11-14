package net.bowspleef.ranked.dto;

import java.time.OffsetDateTime;

public record PartyInviteResponse(
        Long partyId,
        PlayerSummaryResponse player,
        OffsetDateTime createdAt,
        OffsetDateTime expiresAt
) {}
