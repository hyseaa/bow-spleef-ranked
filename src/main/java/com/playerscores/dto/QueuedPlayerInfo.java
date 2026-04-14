package com.playerscores.dto;

import java.util.UUID;

public record QueuedPlayerInfo(
        UUID uuid,
        String username,
        int elo
) {}
