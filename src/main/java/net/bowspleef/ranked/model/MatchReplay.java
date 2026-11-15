package net.bowspleef.ranked.model;

import lombok.Data;

import java.time.OffsetDateTime;

/** The replay of a match (spec 024, ADR-0026): the plugin's file, opaque to the API, and what it said about it. */
@Data
public class MatchReplay {
    private Long matchId;
    /** The game map the match was played on, as the plugin names it (e.g. {@code dread-pit}). */
    private String map;
    /** The length of the recording, in server ticks (20 a second). */
    private int durationTicks;
    /** The version of the plugin's file format. */
    private int formatVersion;
    private int sizeBytes;
    /** The SHA-256 of {@link #data}, in hex: the same file sent again is recognised. */
    private String sha256;
    private byte[] data;
    private OffsetDateTime createdAt;
}
