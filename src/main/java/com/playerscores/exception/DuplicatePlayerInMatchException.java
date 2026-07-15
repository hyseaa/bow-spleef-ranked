package com.playerscores.exception;

import java.util.UUID;

public class DuplicatePlayerInMatchException extends RuntimeException {
    public DuplicatePlayerInMatchException(UUID playerUuid) {
        super("Player appears more than once in the match: " + playerUuid);
    }

    public DuplicatePlayerInMatchException(String message) {
        super(message);
    }
}
