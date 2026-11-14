package net.bowspleef.ranked.dto;

/** The preferences of a player, with the default for each one never set (spec 016, ADR-0023). */
public record PreferencesResponse(int tntRemovedPercent) {}
