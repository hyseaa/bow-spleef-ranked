package net.bowspleef.ranked.exception;

public class AlreadyInPartyException extends RuntimeException {
    public AlreadyInPartyException(String discordId) {
        super("Player " + discordId + " is already in a party");
    }
}
