package com.playerscores.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "elo")
public record EloProperties(
        int floor,
        int startingElo,
        int provisionalThreshold,
        int establishedThreshold,
        int kProvisional,
        int kDeveloping,
        int kEstablished
) {}
