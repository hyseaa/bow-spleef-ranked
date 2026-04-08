package com.playerscores.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record PlayerResponse(UUID uuid, String username, LocalDateTime firstSeenAt) {}
