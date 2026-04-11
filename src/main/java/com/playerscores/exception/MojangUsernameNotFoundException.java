package com.playerscores.exception;

public class MojangUsernameNotFoundException extends RuntimeException {
    public MojangUsernameNotFoundException(String username) {
        super("Minecraft username not found: " + username);
    }
}
