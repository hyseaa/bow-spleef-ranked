package net.bowspleef.ranked.model;

import lombok.Data;

import java.time.OffsetDateTime;

@Data
public class Match {
    private Long id;
    private String gameType;
    private String source;
    private OffsetDateTime playedAt;
    private Long rankedSeasonId;
    private String idempotencyKey;
    /** The modifiers of the game as a JSON object (spec 010), or null when the match carries none. */
    private String modifiers;
}
