package net.bowspleef.ranked.exception;

/** A match with no replay (spec 024). */
public class ReplayNotFoundException extends RuntimeException {
    public ReplayNotFoundException(Long matchId) {
        super("No replay for match " + matchId);
    }
}
