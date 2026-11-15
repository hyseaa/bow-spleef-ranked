package net.bowspleef.ranked.exception;

/** A replay above {@code replay.max-size-bytes} (spec 024). */
public class ReplayTooLargeException extends RuntimeException {
    public ReplayTooLargeException(long maxSizeBytes) {
        super("A replay is " + maxSizeBytes + " bytes at most");
    }
}
