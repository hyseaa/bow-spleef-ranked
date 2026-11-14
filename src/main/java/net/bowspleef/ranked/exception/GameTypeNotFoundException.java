package net.bowspleef.ranked.exception;

public class GameTypeNotFoundException extends RuntimeException {
    public GameTypeNotFoundException(String name) {
        super("Game type not found: " + name);
    }
}
