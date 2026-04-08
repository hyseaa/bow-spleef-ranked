package com.playerscores.dto;

import java.time.LocalDateTime;
import java.util.List;

public record MatchResponse(Long id, String gameType, LocalDateTime playedAt, List<TeamResponse> teams) {}
