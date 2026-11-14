package net.bowspleef.ranked.dto;

import java.util.List;
import java.util.UUID;

public record PlayerStatsResponse(
        UUID uuid,
        PlayerCasualStatsResponse casual,
        List<PlayerRankedStatsResponse> ranked
) {}
