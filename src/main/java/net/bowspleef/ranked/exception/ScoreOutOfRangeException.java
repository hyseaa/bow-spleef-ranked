package net.bowspleef.ranked.exception;

/** A score of a ranked match above the cap (spec 034, ADR-0034): mapped to {@code 400 VALIDATION_FAILED}. */
public class ScoreOutOfRangeException extends RuntimeException {
    public ScoreOutOfRangeException(String gameType, int score, int max) {
        super("A score of a ranked match of " + gameType + " is at most " + max + " (a best of 7), got " + score);
    }
}
