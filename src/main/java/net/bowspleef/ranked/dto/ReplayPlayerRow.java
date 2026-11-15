package net.bowspleef.ranked.dto;

import lombok.Data;

import java.util.UUID;

/** A player of a match that has a replay (spec 024), for the list. */
@Data
public class ReplayPlayerRow {
    private Long matchId;
    private UUID playerUuid;
}
