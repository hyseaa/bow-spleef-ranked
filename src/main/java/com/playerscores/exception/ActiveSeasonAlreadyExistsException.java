package com.playerscores.exception;

public class ActiveSeasonAlreadyExistsException extends RuntimeException {
    public ActiveSeasonAlreadyExistsException(String gameType) {
        super("An active ranked season already exists for game type: " + gameType);
    }
}
