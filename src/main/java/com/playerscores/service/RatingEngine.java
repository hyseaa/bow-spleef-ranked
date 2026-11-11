package com.playerscores.service;

import com.playerscores.dto.InitialRating;
import com.playerscores.dto.RatingResult;
import com.playerscores.dto.TeamRatingInput;
import com.playerscores.model.RatingAlgorithm;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * A rating algorithm (ADR-0013). Each season is rated by the engine of its {@link RatingAlgorithm},
 * both live and when the season is recomputed.
 */
public interface RatingEngine {

    RatingAlgorithm algorithm();

    /** Rating of a player's first match in a season. */
    InitialRating initialRating();

    /**
     * Rates one match from the players' pre-match ratings. Returns a result for every player of
     * every team; players of empty teams or of a match with fewer than two non-empty teams keep
     * their rating.
     */
    Map<UUID, RatingResult> rateMatch(List<TeamRatingInput> teams);
}
