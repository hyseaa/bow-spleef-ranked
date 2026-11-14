package net.bowspleef.ranked.dto;

import java.util.UUID;

public record QueuedPlayerInfo(
        UUID uuid,
        String username,
        int elo
) {}
