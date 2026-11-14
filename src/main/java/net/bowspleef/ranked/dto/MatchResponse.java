package net.bowspleef.ranked.dto;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record MatchResponse(
        Long id,
        String gameType,
        String gameTypeDisplayName,
        String source,
        OffsetDateTime playedAt,
        List<TeamResponse> teams,
        Long rankedSeasonId,
        Map<UUID, Map<String, Object>> playerStats,
        /** The modifiers of the game (spec 010), or null when the match carries none. */
        Map<String, Object> modifiers
) {

    /** A match with no modifiers: the form used before spec 010. */
    public MatchResponse(Long id, String gameType, String gameTypeDisplayName, String source,
                         OffsetDateTime playedAt, List<TeamResponse> teams, Long rankedSeasonId,
                         Map<UUID, Map<String, Object>> playerStats) {
        this(id, gameType, gameTypeDisplayName, source, playedAt, teams, rankedSeasonId, playerStats, null);
    }
}
