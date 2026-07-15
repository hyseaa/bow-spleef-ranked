package com.playerscores.exception;

public class InvalidTeamSizeException extends RuntimeException {
    public InvalidTeamSizeException(String gameType, int expectedSize, int actualSize) {
        super("Invalid team size for game type " + gameType
                + ": expected " + expectedSize + " player(s) per team, got " + actualSize);
    }
}
