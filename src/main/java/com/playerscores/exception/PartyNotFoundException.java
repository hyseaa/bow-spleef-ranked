package com.playerscores.exception;

public class PartyNotFoundException extends RuntimeException {
    public PartyNotFoundException(Long partyId) {
        super("Party not found: " + partyId);
    }

    public PartyNotFoundException(String discordId) {
        super("Player " + discordId + " is not in a party");
    }
}
