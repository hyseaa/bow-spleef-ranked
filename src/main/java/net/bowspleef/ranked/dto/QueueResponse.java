package net.bowspleef.ranked.dto;

/** ttlMinutes: how long a waiting entry lives before it is removed (the bot tells the player, KI-11). */
public record QueueResponse(
        boolean matched,
        QueuedPlayerInfo opponent,
        int ttlMinutes
) {}
