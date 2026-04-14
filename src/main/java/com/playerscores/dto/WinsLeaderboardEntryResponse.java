package com.playerscores.dto;

import java.util.UUID;

public record WinsLeaderboardEntryResponse(UUID uuid, String username, long wins) {}
