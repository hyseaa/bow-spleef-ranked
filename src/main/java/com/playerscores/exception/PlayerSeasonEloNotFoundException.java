package com.playerscores.exception;

import java.util.UUID;

public class PlayerSeasonEloNotFoundException extends RuntimeException {
    public PlayerSeasonEloNotFoundException(UUID playerUuid, Long seasonId) {
        super("No ELO found for player " + playerUuid + " in season " + seasonId);
    }
}
