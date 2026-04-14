package com.playerscores.dto;

public record QueueResponse(
        boolean matched,
        QueuedPlayerInfo opponent
) {}
