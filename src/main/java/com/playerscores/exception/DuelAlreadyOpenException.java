package com.playerscores.exception;

public class DuelAlreadyOpenException extends RuntimeException {
    public DuelAlreadyOpenException(String gameType) {
        super("These two players already have an open duel for game type " + gameType);
    }
}
