package com.playerscores.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

import java.util.UUID;

@ResponseStatus(HttpStatus.NOT_FOUND)
public class PlayerNotFoundException extends RuntimeException {
    public PlayerNotFoundException(Long id) {
        super("Player not found: " + id);
    }

    public PlayerNotFoundException(UUID uuid) {
        super("Player not found: " + uuid);
    }

    public PlayerNotFoundException(String discordId) {
        super("Player not found for Discord ID: " + discordId);
    }
}
