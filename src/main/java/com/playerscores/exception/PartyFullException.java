package com.playerscores.exception;

public class PartyFullException extends RuntimeException {
    public PartyFullException(Long partyId, int maxSize) {
        super("Party " + partyId + " is full (max " + maxSize + " members)");
    }
}
