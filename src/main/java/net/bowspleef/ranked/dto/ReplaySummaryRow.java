package net.bowspleef.ranked.dto;

import lombok.Data;

import java.time.OffsetDateTime;

/** One line of the list of replays as read from the database (spec 024), without the file. */
@Data
public class ReplaySummaryRow {
    private Long matchId;
    private OffsetDateTime playedAt;
    private String gameType;
    private String map;
    private int durationTicks;
}
