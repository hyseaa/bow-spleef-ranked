package com.playerscores.exception;

public class PlayerNotInPartyException extends RuntimeException {
    public PlayerNotInPartyException(String discordId) {
        super("Player " + discordId + " is not in a party");
    }

    public PlayerNotInPartyException(String discordId, Long partyId) {
        super("Player " + discordId + " is not a member of party " + partyId);
    }
}
