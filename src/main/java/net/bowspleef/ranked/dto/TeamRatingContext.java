package net.bowspleef.ranked.dto;

import java.util.List;

public record TeamRatingContext(long teamId, int score, List<PlayerRating> players) {}
