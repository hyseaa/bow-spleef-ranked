package net.bowspleef.ranked.dto;

import java.time.OffsetDateTime;
import java.util.List;

/** One replay of the list (spec 024): the match, its map and length, its players; never the file. */
public record ReplaySummaryResponse(Long matchId, OffsetDateTime playedAt, String gameType, String map,
                                    int durationTicks, List<PlayerSummaryResponse> players) {}
