package net.bowspleef.ranked.dto;

import net.bowspleef.ranked.model.RatingAlgorithm;

import java.time.OffsetDateTime;

public record RankedSeasonResponse(Long id, String name, OffsetDateTime startsAt, OffsetDateTime endsAt, String gameType, boolean active,
                                   RatingAlgorithm ratingAlgorithm) {}
