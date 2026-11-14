package net.bowspleef.ranked.dto;

public record GameTypeResponse(String name, String displayName, boolean ranked, int teamSize, boolean active) {}
