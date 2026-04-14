package com.playerscores.dto;

import java.time.OffsetDateTime;

public record RankedSeasonResponse(Long id, String name, OffsetDateTime startsAt, OffsetDateTime endsAt, String gameType, boolean active) {}
