package net.bowspleef.ranked.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record MatchResponse(
        Long id,
        String gameType,
        String gameTypeDisplayName,
        String source,
        /** The name to show for the source (spec 033), or null when the source is not registered. */
        String sourceDisplayName,
        /** The kind of the source, {@code bot} or {@code custom_server} (spec 033), or null when it is not registered. */
        String sourceKind,
        OffsetDateTime playedAt,
        List<TeamResponse> teams,
        Long rankedSeasonId,
        Map<UUID, Map<String, Object>> playerStats,
        /** The modifiers of the game (spec 010), or null when the match carries none. */
        Map<String, Object> modifiers,
        /** A private game of a custom server's party (spec 038, ADR-0036). */
        @JsonProperty("private") boolean privateMatch
) {

    /** A match that is not private: the form used before spec 038. */
    public MatchResponse(Long id, String gameType, String gameTypeDisplayName, String source, String sourceDisplayName,
                         String sourceKind, OffsetDateTime playedAt, List<TeamResponse> teams, Long rankedSeasonId,
                         Map<UUID, Map<String, Object>> playerStats, Map<String, Object> modifiers) {
        this(id, gameType, gameTypeDisplayName, source, sourceDisplayName, sourceKind, playedAt, teams, rankedSeasonId,
                playerStats, modifiers, false);
    }

    /** A match whose source is not resolved: the form used before spec 033. */
    public MatchResponse(Long id, String gameType, String gameTypeDisplayName, String source,
                         OffsetDateTime playedAt, List<TeamResponse> teams, Long rankedSeasonId,
                         Map<UUID, Map<String, Object>> playerStats, Map<String, Object> modifiers) {
        this(id, gameType, gameTypeDisplayName, source, null, null, playedAt, teams, rankedSeasonId, playerStats,
                modifiers);
    }

    /** A match with no modifiers and no resolved source: the form used before spec 010. */
    public MatchResponse(Long id, String gameType, String gameTypeDisplayName, String source,
                         OffsetDateTime playedAt, List<TeamResponse> teams, Long rankedSeasonId,
                         Map<UUID, Map<String, Object>> playerStats) {
        this(id, gameType, gameTypeDisplayName, source, playedAt, teams, rankedSeasonId, playerStats, null);
    }
}
