package net.bowspleef.ranked.exception;

/** A private game cannot be ranked (spec 038, ADR-0036): code {@code PRIVATE_RANKED_MATCH}, 422. */
public class PrivateRankedMatchException extends RuntimeException {
    public PrivateRankedMatchException(String gameType) {
        super("A private match cannot be recorded under the ranked game type " + gameType);
    }
}
