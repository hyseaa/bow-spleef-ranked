package com.playerscores.dto;

import java.time.OffsetDateTime;
import java.util.List;

public record MatchResponse(Long id, String gameType, String gameTypeDisplayName, String source, OffsetDateTime playedAt, List<TeamResponse> teams, Long rankedSeasonId) {}
