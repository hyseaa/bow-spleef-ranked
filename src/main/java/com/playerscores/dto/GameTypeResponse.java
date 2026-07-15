package com.playerscores.dto;

public record GameTypeResponse(String name, String displayName, boolean ranked, int teamSize, boolean active) {}
