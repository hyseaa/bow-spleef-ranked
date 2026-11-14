package net.bowspleef.ranked.dto;

import java.util.List;

public record QueueByGameTypeResponse(
        String gameType,
        String displayName,
        List<QueuedPlayerInfo> players
) {}
