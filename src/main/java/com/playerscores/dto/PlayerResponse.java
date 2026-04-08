package com.playerscores.dto;

import java.time.LocalDateTime;

public record PlayerResponse(Long id, String username, LocalDateTime createdAt) {}
