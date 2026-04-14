package com.playerscores.dto;

import java.util.UUID;

public record PlayerEloSnapshot(UUID playerUuid, int elo, int matchesPlayed) {}
