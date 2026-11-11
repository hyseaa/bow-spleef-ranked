package com.playerscores.dto;

import java.util.UUID;

/** A player's rating after a match: displayed Elo plus the OpenSkill parameters (kept unchanged by classic Elo). */
public record RatingResult(UUID playerUuid, int elo, double mu, double sigma) {}
