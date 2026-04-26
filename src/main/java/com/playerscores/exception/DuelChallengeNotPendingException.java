package com.playerscores.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.CONFLICT)
public class DuelChallengeNotPendingException extends RuntimeException {
    public DuelChallengeNotPendingException(Long id, String currentStatus) {
        super("Duel challenge " + id + " cannot transition from status: " + currentStatus);
    }
}
