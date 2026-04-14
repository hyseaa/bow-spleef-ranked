package com.playerscores.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.NOT_FOUND)
public class RankedSeasonNotFoundException extends RuntimeException {
    public RankedSeasonNotFoundException(Long id) {
        super("Ranked season not found: " + id);
    }
}
