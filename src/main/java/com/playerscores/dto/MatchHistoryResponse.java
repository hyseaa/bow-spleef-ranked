package com.playerscores.dto;

import java.time.OffsetDateTime;
import java.util.List;

public record MatchHistoryResponse(
        Long matchId,
        OffsetDateTime playedAt,
        int eloChange,
        int eloAfter,
        List<PlayerSummaryResponse> opponents
) {}
