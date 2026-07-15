package com.playerscores.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * OpenSkill (Weng-Lin Plackett-Luce) rating configuration.
 * The displayed integer elo is derived from the ordinal (mu - 3*sigma):
 * elo = max(floor, round(startingElo + ordinalScale * ordinal)).
 */
@ConfigurationProperties(prefix = "elo")
public record EloProperties(
        @DefaultValue("100") int floor,
        @DefaultValue("1000") int startingElo,
        @DefaultValue("15") double ordinalScale,
        @DefaultValue("25.0") double mu,
        @DefaultValue("8.333333333333334") double sigma,
        @DefaultValue("4.166666666666667") double beta,
        @DefaultValue("0.0001") double kappa,
        @DefaultValue("0.0") double tau
) {}
