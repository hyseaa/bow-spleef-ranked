package com.playerscores.dto;

import java.util.List;

/** One team of a match as seen by a {@link com.playerscores.service.RatingEngine}: its score and its players' pre-match ratings. */
public record TeamRatingInput(long teamId, int score, List<PlayerEloSnapshot> players) {}
