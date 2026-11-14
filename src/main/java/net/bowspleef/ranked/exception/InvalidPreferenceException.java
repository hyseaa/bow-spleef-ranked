package net.bowspleef.ranked.exception;

/** A preference that cannot be kept: out of its range, or an update that changes nothing (spec 016). */
public class InvalidPreferenceException extends RuntimeException {
    public InvalidPreferenceException(String message) {
        super(message);
    }
}
