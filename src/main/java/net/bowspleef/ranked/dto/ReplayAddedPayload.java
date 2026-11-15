package net.bowspleef.ranked.dto;

/** Webhook body sent when a match gets its replay, so that the bot adds the line to its announcement (spec 024). */
public record ReplayAddedPayload(String event, Long matchId) {

    public static final String EVENT = "REPLAY_ADDED";

    public ReplayAddedPayload(Long matchId) {
        this(EVENT, matchId);
    }
}
