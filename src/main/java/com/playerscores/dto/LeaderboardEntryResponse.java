package com.playerscores.dto;

import java.util.UUID;

public record LeaderboardEntryResponse(UUID uuid, String username, long wins) {}
