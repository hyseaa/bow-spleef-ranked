package com.playerscores.dto;

import java.time.OffsetDateTime;

public record EloHistoryResponse(Long matchId, int eloBefore, int eloAfter, int eloChange, OffsetDateTime recordedAt) {}
