package net.bowspleef.ranked.exception;

/** A match that already has a replay, sent other bytes (spec 024). */
public class ReplayAlreadyExistsException extends RuntimeException {
    public ReplayAlreadyExistsException(Long matchId) {
        super("Match " + matchId + " already has another replay");
    }
}
