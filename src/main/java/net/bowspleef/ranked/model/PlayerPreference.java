package net.bowspleef.ranked.model;

import lombok.Data;

import java.time.OffsetDateTime;
import java.util.UUID;

/** The preferences a player has set (spec 016, ADR-0023): a row exists only once a preference was changed. */
@Data
public class PlayerPreference {
    private UUID playerUuid;
    /** The share of the map's TNT already removed when a game starts, 0 to 100. */
    private int tntRemovedPercent;
    private OffsetDateTime updatedAt;
}
