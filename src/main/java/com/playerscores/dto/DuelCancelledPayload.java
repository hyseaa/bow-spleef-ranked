package com.playerscores.dto;

/**
 * Webhook body sent when an ACTIVE or REPORTED duel becomes CANCELLED, so that the bot can close the duel
 * channel (spec 003). {@code reason} is {@code AGREEMENT} or {@code NO_ACTIVE_RANKED_SEASON}.
 */
public record DuelCancelledPayload(String event, Long duelId, String reason) {

    public static final String EVENT = "DUEL_CANCELLED";
    public static final String REASON_AGREEMENT = "AGREEMENT";
    public static final String REASON_NO_ACTIVE_SEASON = "NO_ACTIVE_RANKED_SEASON";

    public DuelCancelledPayload(Long duelId, String reason) {
        this(EVENT, duelId, reason);
    }
}
