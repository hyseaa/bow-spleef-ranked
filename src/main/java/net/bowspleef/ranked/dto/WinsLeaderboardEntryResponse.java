package net.bowspleef.ranked.dto;

import java.util.UUID;

public record WinsLeaderboardEntryResponse(UUID uuid, String username, long wins) {}
