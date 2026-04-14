package com.playerscores.client;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.UUID;

@Slf4j
@Component
public class HypixelClient {

    private final RestClient restClient;
    private final String apiKey;

    public HypixelClient(RestClient.Builder builder, @Value("${hypixel.api-key}") String apiKey) {
        this.restClient = builder.baseUrl("https://api.hypixel.net").build();
        this.apiKey = apiKey;
    }

    public String getLinkedDiscord(UUID uuid) {
        log.debug("Fetching Hypixel player data for uuid={}", uuid);
        try {
            HypixelResponse resp = restClient.get()
                    .uri("/v2/player?uuid={uuid}", uuid)
                    .header("API-Key", apiKey)
                    .retrieve()
                    .body(HypixelResponse.class);
            if (resp == null || resp.player() == null) {
                log.warn("Hypixel returned no player for uuid={}", uuid);
                return null;
            }
            HypixelSocialMedia socialMedia = resp.player().socialMedia();
            if (socialMedia == null || socialMedia.links() == null) {
                log.warn("No social media links found on Hypixel for uuid={}", uuid);
                return null;
            }
            String discord = socialMedia.links().discord();
            if (discord == null) {
                log.warn("No Discord linked on Hypixel for uuid={}", uuid);
            } else {
                log.debug("Found linked Discord={} for uuid={}", discord, uuid);
            }
            return discord;
        } catch (RestClientException e) {
            log.error("Hypixel API request failed for uuid={}: {}", uuid, e.getMessage(), e);
            return null;
        }
    }

    private record HypixelResponse(HypixelPlayer player) {}
    private record HypixelPlayer(HypixelSocialMedia socialMedia) {}
    private record HypixelSocialMedia(HypixelLinks links) {}
    private record HypixelLinks(@JsonProperty("DISCORD") String discord) {}
}
