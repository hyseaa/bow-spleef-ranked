package com.playerscores.dto;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record MatchResponse(
        Long id,
        String gameType,
        String gameTypeDisplayName,
        String source,
        OffsetDateTime playedAt,
        List<TeamResponse> teams,
        Long rankedSeasonId,
        Map<UUID, Map<String, Object>> playerStats
) {}
