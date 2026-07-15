package com.playerscores.exception;

public class PartyInviteNotFoundException extends RuntimeException {
    public PartyInviteNotFoundException(Long partyId, String discordId) {
        super("No pending invite to party " + partyId + " for player " + discordId);
    }
}
