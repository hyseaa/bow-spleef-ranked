package net.bowspleef.ranked.dto;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * The preferences to change (spec 016, ADR-0023): the fields that are present. {@code tntRemovedPercent} is the
 * share of the map's TNT already removed when a game starts, a whole number from 0 to 100.
 */
public record UpdatePreferencesRequest(
        @Min(0) @Max(100) @JsonDeserialize(using = WholeNumberDeserializer.class) Integer tntRemovedPercent) {}
