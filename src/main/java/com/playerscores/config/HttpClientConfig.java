package com.playerscores.config;

import org.springframework.boot.web.client.ClientHttpRequestFactories;
import org.springframework.boot.web.client.ClientHttpRequestFactorySettings;
import org.springframework.boot.web.client.RestClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * Timeouts for every outgoing HTTP call (Mojang, Hypixel). Without them a call that is never answered waits for
 * two minutes, inside a database transaction (a ranked match holds the season lock while it resolves usernames)
 * and with a request thread blocked. The match webhook sets its own, shorter ones.
 */
@Configuration
public class HttpClientConfig {

    static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(3);
    static final Duration READ_TIMEOUT = Duration.ofSeconds(5);

    @Bean
    public RestClientCustomizer outgoingHttpTimeouts() {
        return builder -> builder.requestFactory(ClientHttpRequestFactories.get(
                ClientHttpRequestFactorySettings.DEFAULTS
                        .withConnectTimeout(CONNECT_TIMEOUT)
                        .withReadTimeout(READ_TIMEOUT)));
    }
}
