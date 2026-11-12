package com.playerscores.exception;

public class QueueTeamGameTypeException extends RuntimeException {
    public QueueTeamGameTypeException(String gameType, int teamSize) {
        super("The queue only supports 1v1 game types: " + gameType + " has " + teamSize + " players per team");
    }
}
