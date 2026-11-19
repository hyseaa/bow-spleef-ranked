package net.bowspleef.ranked.model;

/** The rating algorithm of a season (ADR-0013); stored in ranked_season.rating_algorithm. */
public enum RatingAlgorithm {
    /** Classic Elo with experience-based K-factor: seasons played before 2026-07-16. */
    CLASSIC_ELO,
    /** OpenSkill (Weng-Lin Plackett-Luce), one update per match: the seasons from 2026-07-16 to spec 034. */
    OPENSKILL,
    /**
     * OpenSkill by games (spec 034, ADR-0034): a match of two teams scored a-b is a + b games, rated as the mean over
     * every order of the game-by-game updates. Every new season, and the season active at its deployment.
     */
    OPENSKILL_GAMES
}
