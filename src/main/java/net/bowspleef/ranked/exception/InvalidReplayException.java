package net.bowspleef.ranked.exception;

/** A replay whose file or parameters cannot be kept (spec 024). */
public class InvalidReplayException extends RuntimeException {
    public InvalidReplayException(String message) {
        super(message);
    }
}
