package com.playerscores.exception;

/** Mojang or Hypixel could not answer (outage, rate limit, bad Hypixel key): not the player's fault (KI-21). */
public class UpstreamUnavailableException extends RuntimeException {
    public UpstreamUnavailableException(String service, Throwable cause) {
        super(service + " is unavailable right now, please try again later", cause);
    }
}
