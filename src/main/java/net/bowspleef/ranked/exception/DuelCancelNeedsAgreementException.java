package net.bowspleef.ranked.exception;

public class DuelCancelNeedsAgreementException extends RuntimeException {
    public DuelCancelNeedsAgreementException(Long id, String status) {
        super("Duel challenge " + id + " is " + status + ": it can only be cancelled with the other player's agreement");
    }
}
