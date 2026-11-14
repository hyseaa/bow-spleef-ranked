package net.bowspleef.ranked.dto;

import java.util.UUID;

public record PlayerRating(UUID playerUuid, double mu, double sigma) {}
