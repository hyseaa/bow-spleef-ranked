package com.playerscores.dto;

import java.time.LocalDateTime;

public record ScoreResponse(Long id, Long playerId, Integer value, String game, LocalDateTime createdAt) {}
