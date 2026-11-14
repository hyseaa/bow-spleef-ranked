package net.bowspleef.ranked.dto;

import java.util.List;
import java.util.UUID;

public record PlayerStatsResponse(
        UUID uuid,
        List<PlayerRankedStatsResponse> ranked,
        /** One entry per non-ranked game type the player has played, the most played first; no total (ADR-0022). */
        List<PlayerGameTypeStatsResponse> gameTypes
) {}
