package com.playerscores.dto;

import java.util.UUID;

public record LeaderboardRow(UUID uuid, long wins) {}
