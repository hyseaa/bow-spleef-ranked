package net.bowspleef.ranked.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Classic Elo configuration (seasons played before 2026-07-16, ADR-0013). Values as they were
 * until api@29a4e2b; changing them changes every classic season's recompute.
 */
@ConfigurationProperties(prefix = "elo.classic")
public record ClassicEloProperties(
        @DefaultValue("100") int floor,
        @DefaultValue("1000") int startingElo,
        @DefaultValue("10") int provisionalThreshold,
        @DefaultValue("30") int establishedThreshold,
        @DefaultValue("40") int kProvisional,
        @DefaultValue("20") int kDeveloping,
        @DefaultValue("10") int kEstablished
) {}
