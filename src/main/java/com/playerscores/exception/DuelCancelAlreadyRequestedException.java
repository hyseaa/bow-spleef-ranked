package com.playerscores.exception;

public class DuelCancelAlreadyRequestedException extends RuntimeException {
    public DuelCancelAlreadyRequestedException(Long id) {
        super("A cancellation request is already waiting on duel challenge " + id);
    }
}
