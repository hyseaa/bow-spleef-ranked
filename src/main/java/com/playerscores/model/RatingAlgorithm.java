package com.playerscores.model;

/** The rating algorithm of a season (ADR-0013); stored in ranked_season.rating_algorithm. */
public enum RatingAlgorithm {
    /** Classic Elo with experience-based K-factor: seasons played before 2026-07-16. */
    CLASSIC_ELO,
    /** OpenSkill (Weng-Lin Plackett-Luce): every season since 2026-07-16. */
    OPENSKILL
}
