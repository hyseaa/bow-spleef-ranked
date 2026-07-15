package com.playerscores.exception;

public class AlreadyInPartyException extends RuntimeException {
    public AlreadyInPartyException(String discordId) {
        super("Player " + discordId + " is already in a party");
    }
}
