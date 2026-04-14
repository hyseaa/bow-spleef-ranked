package com.playerscores.dto;

import java.util.List;

public record QueueByGameTypeResponse(
        String gameType,
        String displayName,
        List<QueuedPlayerInfo> players
) {}
