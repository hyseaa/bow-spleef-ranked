package com.playerscores.dto;

import java.util.List;

public record PlayerStatsResponse(
        PlayerCasualStatsResponse casual,
        List<PlayerRankedStatsResponse> ranked
) {}
