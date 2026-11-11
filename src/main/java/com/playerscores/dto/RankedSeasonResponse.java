package com.playerscores.dto;

import com.playerscores.model.RatingAlgorithm;

import java.time.OffsetDateTime;

public record RankedSeasonResponse(Long id, String name, OffsetDateTime startsAt, OffsetDateTime endsAt, String gameType, boolean active,
                                   RatingAlgorithm ratingAlgorithm) {}
