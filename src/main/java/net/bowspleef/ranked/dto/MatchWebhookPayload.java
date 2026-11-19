package net.bowspleef.ranked.dto;

import com.fasterxml.jackson.annotation.JsonUnwrapped;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

@Getter
@AllArgsConstructor
public class MatchWebhookPayload {
    @JsonUnwrapped
    private final MatchResponse match;
    private final List<PlayerRankEntry> playerRanks;
    /**
     * The key the match was recorded with ({@code Idempotency-Key}, spec 002), or null without one: the id of its live
     * game, so that the bot turns the live message into the announcement (spec 036, REQ-036-12).
     */
    private final String idempotencyKey;

    /** A match recorded without an idempotency key. */
    public MatchWebhookPayload(MatchResponse match, List<PlayerRankEntry> playerRanks) {
        this(match, playerRanks, null);
    }
}
