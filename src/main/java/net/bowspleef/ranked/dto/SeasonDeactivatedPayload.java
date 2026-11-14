package net.bowspleef.ranked.dto;

/**
 * Webhook body sent when a ranked season stops being active, so that the bot removes the rank roles of its
 * game type (spec 005).
 */
public record SeasonDeactivatedPayload(String event, Long seasonId, String gameType) {

    public static final String EVENT = "SEASON_DEACTIVATED";

    public SeasonDeactivatedPayload(Long seasonId, String gameType) {
        this(EVENT, seasonId, gameType);
    }
}
