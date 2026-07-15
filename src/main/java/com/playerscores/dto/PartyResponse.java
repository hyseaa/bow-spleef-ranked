package com.playerscores.dto;

import java.time.OffsetDateTime;
import java.util.List;

public record PartyResponse(
        Long id,
        PlayerSummaryResponse leader,
        List<PlayerSummaryResponse> members,
        List<PartyInviteResponse> pendingInvites,
        OffsetDateTime createdAt
) {}
