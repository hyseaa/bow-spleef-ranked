package com.playerscores.exception;

public class NoActiveRankedSeasonException extends RuntimeException {
    public NoActiveRankedSeasonException() {
        super("No active ranked season found");
    }
}
