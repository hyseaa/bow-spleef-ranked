package com.playerscores.exception;

import lombok.Getter;

@Getter
public class DiscordMismatchException extends RuntimeException {

    private final String linkedDiscordUsername;

    public DiscordMismatchException(String linkedDiscordUsername) {
        super("The Discord linked on Hypixel does not match your account.");
        this.linkedDiscordUsername = linkedDiscordUsername;
    }

}
