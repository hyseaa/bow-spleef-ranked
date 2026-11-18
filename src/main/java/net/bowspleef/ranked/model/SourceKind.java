package net.bowspleef.ranked.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * How the matches of a source are recorded (spec 033, ADR-0033); stored in source.kind as its lower-case value.
 */
public enum SourceKind {
    /** A duel reported and confirmed in Discord through the bot, wherever it was played. */
    BOT("bot"),
    /** A game sent by the plugin of a custom server. */
    CUSTOM_SERVER("custom_server");

    private final String value;

    SourceKind(String value) {
        this.value = value;
    }

    /** The value stored in the database and written in JSON. */
    @JsonValue
    public String value() {
        return value;
    }

    /** The kind of a stored or received value; an unknown value is refused. */
    @JsonCreator
    public static SourceKind fromValue(String value) {
        for (SourceKind kind : values()) {
            if (kind.value.equals(value)) {
                return kind;
            }
        }
        throw new IllegalArgumentException("Unknown source kind: " + value);
    }
}
