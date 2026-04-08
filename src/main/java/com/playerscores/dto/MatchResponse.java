package com.playerscores.dto;

import java.time.LocalDateTime;
import java.util.List;

public record MatchResponse(Long id, String gameType, String source, LocalDateTime playedAt, List<TeamResponse> teams) {}
