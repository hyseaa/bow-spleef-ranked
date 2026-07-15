package com.playerscores.dto;

import java.util.UUID;

public record PlayerRating(UUID playerUuid, double mu, double sigma) {}
