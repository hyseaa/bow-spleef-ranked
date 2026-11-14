package net.bowspleef.ranked.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.FORBIDDEN)
public class DuelChallengeForbiddenException extends RuntimeException {
    public DuelChallengeForbiddenException(String message) {
        super(message);
    }
}
