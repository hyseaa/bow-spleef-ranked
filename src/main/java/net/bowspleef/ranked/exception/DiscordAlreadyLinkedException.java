package net.bowspleef.ranked.exception;

/** The Discord account is already linked to another Minecraft account. */
public class DiscordAlreadyLinkedException extends RuntimeException {
    public DiscordAlreadyLinkedException() {
        super("This Discord account is already linked to another Minecraft account");
    }
}
