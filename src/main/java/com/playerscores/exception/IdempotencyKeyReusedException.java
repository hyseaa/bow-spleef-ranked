package com.playerscores.exception;

public class IdempotencyKeyReusedException extends RuntimeException {
    public IdempotencyKeyReusedException(String source, String key) {
        super("Idempotency key '" + key + "' was already used by source '" + source + "' for a different match");
    }
}
